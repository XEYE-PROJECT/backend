# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

XEYE backend, **rebuilt in Java** (was FastAPI/Python at `../XEYE-backend`). It is a
**modular monolith with hexagonal architecture**: one module per aggregate
(`user`, `apikey`, `list`, `element`, `training`, `search`) plus a `shared` module. Each module
has its own `domain` → `application` → `infrastructure` layers (dependencies point inward only).
The `search` module owns the integration with the rebuilt search microservice
(`../search-service`): the `searches` log table, the `/internal/search/*` sync API, the
change notifications pushed to it and the console playground proxy (see "Search-service
integration" below).

Scope is deliberately smaller than the Python original (university project): no billing
(`calls`/`endpoints`), no refresh tokens, no email verification.

## Tech stack

- **Java 17**, **Spring Boot 4.1** (Spring Framework 7, Hibernate 7), Maven.
- **MariaDB** via **Flyway** migrations (`src/main/resources/db/migration`).
- **JWT access tokens** (jjwt) + **BCrypt** (Spring Security), stateless.
- Build runs on JDK 25 targeting `--release 17`.

## Spring Boot 4 gotchas (IMPORTANT — these bit us, do not "fix" them back)

1. **Jackson 3.** Boot 4 ships Jackson 3 under the **`tools.jackson.*`** packages
   (`tools.jackson.databind.ObjectMapper`, `tools.jackson.core.type.TypeReference`). The
   auto-configured `ObjectMapper` bean is the Jackson 3 one — inject that type, not
   `com.fasterxml.jackson.databind.ObjectMapper`. **Annotations stayed** at
   `com.fasterxml.jackson.annotation.*` (`@JsonProperty`, `@JsonInclude`) — those are correct.
2. **Web starter does not pull Jackson.** `spring-boot-starter-webmvc` (the renamed
   `-web`) needs an explicit **`spring-boot-starter-json`** for Jackson on the classpath.
3. **Flyway needs its integration module.** `flyway-core` alone does NOT run migrations in
   Boot 4 — auto-config lives in **`org.springframework.boot:spring-boot-flyway`**. Symptom
   if missing: app starts, no `Flyway` log lines, tables never created, `Table '…' doesn't exist`.
4. **jjwt uses Gson here** (`jjwt-gson`, not `jjwt-jackson`) so it does not drag Jackson 2
   onto the classpath and clash with Boot's Jackson 3.
5. Test starter is `spring-boot-starter-webmvc-test`.

## Architecture rules

- Layer deps inward: `infrastructure → application → domain`. Domain is plain Java (no
  Spring/JPA annotations). Adapters implement ports.
- **Ports:** outbound ports (`application/port/out`, e.g. `*Repository`, `TrainingLauncher`,
  `SearchIndexer`) are implemented by adapters in `infrastructure`. Inbound ports
  (`application/port/in`, the `*UseCases` interfaces) are called by controllers.
- **Repositories return domain objects**, never JPA entities (`*Mapper` + `*PersistenceAdapter`).
- **Cross-module interaction** (keep it acyclic):
  - `element → list` and `training → list`/`element` call the *internal in-port interfaces*
    `ListQueryPort` / `ElementQueryPort` directly (constructor injection). Fine — these are
    application-layer interfaces, not infrastructure.
  - The **edit → training trigger goes through a Spring event** (`TrainingRequestedEvent` in
    `shared/event`) so `list`/`element` never depend on `training` at compile time.
- **Auth:** `JwtAuthenticationFilter` (wired in `SecurityConfig`, not a `@Component`) puts an
  `AuthenticatedUser(id, email, permission)` principal in the context. Controllers read it via
  `@AuthenticationPrincipal AuthenticatedUser`. Ownership is enforced in services by `userId`.
  **Service-to-service auth** (`/webhooks/**` with `X-Webhook-Token`, `/internal/**` with
  `X-Internal-Token`) is done by `SharedSecretAuthenticationFilter` in `SecurityConfig`
  (constant-time compare via `SecretTokens`, fail-closed, 403 before the controller; routes
  require `ROLE_TRAINING_WORKER` / `ROLE_SEARCH_SERVICE`). Controllers never check secrets.
- **Config safety:** no default Spring profile. `application.yml` has NO defaults for secrets,
  DB, providers or CORS; `application-dev.yml` holds the dev defaults (activated by
  `mvn spring-boot:run` via the pom, by `docker-compose.dev.yml`, or `SPRING_PROFILES_ACTIVE=dev`);
  `application-prod.yml` + `ProductionConfigGuard` (`@Profile("prod")`) abort startup on dev or
  weak values. The production `Dockerfile` sets `SPRING_PROFILES_ACTIVE=prod`. Properties records
  are `@Validated`.
- **Accounts (module `user`):** `AuthService` (register/login/verify/reset/MFA/SSO/logout),
  `UserService` (own account), `AdminUserService` (`/admin/**`, `@PreAuthorize("hasRole('ADMIN')")`
  + `@EnableMethodSecurity`). Register never auto-logs in and always answers 202 (no email
  enumeration; an existing address gets an "account exists" email). Login requires a verified
  email (`AUTH_REQUIRE_EMAIL_VERIFICATION`), runs BCrypt even for unknown emails, and applies a
  **progressive per-account lock** persisted on `users` (5 failures → 1, 2, 4… min, capped 60;
  `@Transactional(noRollbackFor = DomainException)` so the counter survives the 401). IP rate limit
  for `POST /auth/*` lives in `AuthRateLimitFilter` (in-memory fixed window). **Sessions:** JWTs
  carry `jti` + `ver` + `purpose=access`; `JwtAuthenticationFilter` rejects revoked jtis
  (`revoked_tokens`, cached in memory) and stale `users.token_version` (bumped by password/email
  change, 2FA enable, logout-all). Special-purpose JWTs (`mfa`, `sso_state`) never authenticate.
  One-time email tokens (`user_tokens`, SHA-256 hashed, 24 h verify / 1 h reset & change-email)
  are issued by `AccountTokenService`; emails go through the `EmailSender` port (`log` in dev,
  `smtp` = IONOS `noreply@xeye.es` with reply-to `info@xeye.es`, or `resend`). 2FA = RFC 6238 TOTP
  (`shared/security/Totp`, pure Java) + 10 hashed recovery codes. SSO = hand-rolled OIDC
  authorization-code flow in `user/infrastructure/sso/OidcClient` (Google/Microsoft, JWKS via jjwt),
  stateless signed `state`, one-time exchange code so the JWT never travels in a URL. Security
  events go to the `xeye.audit` logger (`AuthAuditLog`, emails masked). `ADMIN_EMAILS` promotes
  listed accounts to admin at login (first-admin bootstrap; `DevAdminSeeder` only in dev).
- **API keys are hashed:** `api_keys.key_hash` (SHA-256 hex, `ApiKeyHasher`) + `key_prefix`;
  the raw value exists only in the `POST /api-keys` response (`ApiKeyCreatedResponse`). The
  search bootstrap/sync carry `keyHash`, never the key. Migration V6 hashed existing rows with
  `SHA2()` (identical output).
- **Errors:** throw `shared.exception.*` (`NotFoundException`→404, `ConflictException`→409,
  `BadRequestException`→400, `UnauthorizedException`→401, `ForbiddenException`→403,
  `TooManyRequestsException`→429 + Retry-After, `ServiceUnavailableException`→503,
  `PayloadTooLargeException`→413). `GlobalExceptionHandler` maps them **and the framework's**
  (malformed JSON, type mismatch, unknown route → 404, wrong method → 405, bean validation,
  optimistic lock → 409 `CONCURRENT_MODIFICATION`, `DataIntegrityViolation` → 409) to `ApiError`
  JSON, always with a machine `code`; only truly unexpected exceptions become 500. Domain
  `IllegalArgumentException`s (blank text, unknown status) map to 400 `INVALID_ARGUMENT`. Do not
  catch them in controllers.
- **Pagination:** every list endpoint takes `?offset&limit` (`shared/paging/Paging.of`, max 200)
  and returns `PageResponse {items, total, offset, limit}`; repositories return
  `shared/paging/Page`. The internal search API paginates by keyset (`afterId`).
- **Body limits:** `BodySizeLimitFilter` (registered first in `WebConfig`, `xeye.http.*`) rejects
  oversized bodies with 413 before parsing: 1 MB default, 16 MB on `/elements/import`, 8 MB on
  `/internal/**`, 256 MB on `/webhooks/**` (base64 embeddings of a whole list). Request DTOs carry
  `@Size` limits and the domain re-checks lengths (`Element.MAX_*`, `ItemList.MAX_*`).
- **Optimistic locking:** `lists`, `elements` and `trainings` have a `@Version` column
  (migration V9). Domain objects carry `version` and the mappers copy it back; bulk JPQL updates
  bump it (`version = version + 1`). Re-read an entity after a bulk update before saving it.
- **Batch writes:** `ElementPersistenceAdapter.saveAll` / `updateGeneratedDescriptions` go through
  `JdbcTemplate.batchUpdate` (IDENTITY ids make Hibernate insert batching impossible;
  `rewriteBatchedStatements=true` turns each batch into one multi-row INSERT).
- **Outbox instead of `@Async` events:** every `shared/event/*Event` implements `DomainEvent`
  (with a stable `TYPE`). `OutboxRecorder` (`@EventListener`, synchronous) writes it to
  `outbox_events` **in the publisher's transaction**; `OutboxRelay` (single thread, woken after
  each commit + `xeye.outbox.poll-ms` poll) delivers it to the module `OutboxHandler` bean that
  declares the type (`SearchSyncOutboxHandler` in `search`, `TrainingOutboxHandler` in
  `training`), coalescing identical rows, with exponential backoff and `failed` after
  `max-attempts` (ERROR log → Sentry). Handlers must be idempotent. Never make an outbound HTTP
  call inside a DB transaction: publish an event and let the relay do it.
- **Outbound HTTP:** build clients with `shared/http/OutboundHttp.client(url, readTimeout)`
  (connect timeout 3 s, HTTP/1.1). The three search-service clients share the
  `searchServiceBreaker` `CircuitBreaker` bean (5 failures → open 30 s → probe); `Retry` exists for
  idempotent calls. RunPod's `POST /run` is never retried (it would launch twice).
- **DB-managed timestamps:** JPA entities use `@Generated` + `insertable=false, updatable=false`
  on `created_at`/`updated_at`; the DB defaults / `ON UPDATE` manage them.

## The training flow (the crux)

Trigger → pending → enqueue (user) → dispatch → callback → activate. All wiring lives in the
`training` module.

1. **Trigger.** `ListService.update` (when the description changes) and `ElementService`
   (element created / text|description changed / deleted) publish `TrainingRequestedEvent`.
   It goes through the **outbox** (`TrainingOutboxHandler`) and only calls `ensurePending`: a
   `Training` row `PENDING` per list (at most one, DB-enforced by the V4 generated-column unique
   index). Nothing launches on its own.
2. **Enqueue (user-initiated).** `POST /trainings/{id}/launch` (launch the pending one) or
   `POST /lists/{listId}/trainings` (retrain now: reuses/creates the pending row) →
   `TrainingDispatcher.launch` → `TrainingService.enqueue` (PENDING → `QUEUED`, stores the run
   options: `embedding_model`, `force_enrich`, `strategy`). 409 if the list already has a run
   queued or launched. Then the dispatcher runs `dispatch()` right away.
3. **Dispatch (the queue).** `TrainingDispatcher.dispatch()` (after each enqueue, after each
   terminal webhook, after the stalled sweep, and every 5 s as a fallback; serialized by a
   `tryLock`) asks `pickNextQueued()`: nothing if `countLaunched() >= max-concurrent`
   (`TRAINING_MAX_CONCURRENT`, `<=0` = unlimited); otherwise, among the queued runs whose user is
   under `max-concurrent-per-user` (`TRAINING_MAX_CONCURRENT_PER_USER`), the one whose user has
   the **fewest launched runs**, oldest first (fairness). For it, `prepareLaunch(trainingId)`
   marks **all** the list's elements `trained=false`, builds the payload, records the launch-time
   element ids on `trainings.element_ids` (search aligns embedding rows by id) and fixes the
   price; then `TrainingLauncher.launch(...)` **outside any transaction**, then `markLaunched`
   (sets the instance id; it never regresses a status a fast worker callback already advanced).
   A launch failure marks the run `FAILED` and re-flags the list pending. The webhook secret is
   **not** in the payload: the docker launcher passes it as `-e WEBHOOK_SECRET`, the RunPod
   endpoint has it in its env. Queued runs show `queuePosition` in the API.
3. **Provider** (`xeye.training.provider`). All three send the *same* job payload
   (`TrainingLaunchCommand`, whose component names are snake_case **on purpose** — the Python
   worker reads them literally) and answer on the same webhook; they differ only in where the
   container runs:
   - `mock` (default, dev): `MockTrainingLauncher` simulates completion in-process after a short
     delay by calling `TrainingCompletionHandler.applyUpdate(completed)` — exercises the whole path.
   - `docker`: `DockerTrainingLauncher` writes the job JSON to `xeye.training.docker.input-dir` and
     `docker run -d --rm`s one `../training-service` container per training. Needs the docker socket
     (mounted in `docker-compose.dev.yml`) and `host-input-dir` = the same dir *as the daemon sees
     it* (a bind mount is always resolved on the host). It passes `--gpus` (`docker.gpus`, default
     `all`) and **retries once without it** if the daemon cannot provide a GPU — the GPU is used
     whenever possible, never a reason to fail a training. Only the CUDA image (`Dockerfile.gpu`)
     can actually use it; the launcher always overrides the CMD with the one-shot entrypoint.
   - `runpod`: `RunPodTrainingLauncher` POSTs `https://api.runpod.ai/v2/{endpointId}/run`.
4. **Callback.** `POST /webhooks/training-update` (`TrainingWebhookController`; the
   `X-Webhook-Token` header is verified by `SharedSecretAuthenticationFilter`) →
   `TrainingService.applyUpdate`, a **strict, idempotent state machine**
   (`TrainingStatus.canTransitionTo`): callbacks only move forward, a repeated status is a
   heartbeat, anything on a terminal run is ignored (200 with `applied: false`), `list_id` must
   match (400 `TRAINING_LIST_MISMATCH`). Every callback sets `trainings.last_heartbeat_at`
   (`updated_at` does not change on a same-status heartbeat — Hibernate skips the UPDATE — which
   is why the column exists; `TrainingStalledSweeper` uses it). A run the sweeper marked stalled
   (`Training.STALLED_ERROR`) may still complete late. Concurrent callbacks on the same run hit
   the `@Version` and the controller retries. On `completed`: store the embeddings in
   **`training_embeddings`** (own table, never loaded when listing; `trainings.has_embeddings`
   is the flag), set model/time/cost, cache the worker's `generated_descriptions` on the elements
   (batched, see below), set this training **`in_use=true`** (clearing it on all other trainings
   of the list), mark the list's elements `trained=true`, and publish
   `SearchIndexRequestedEvent` (outbox).
5. **Search push** (`xeye.search.provider`): `TrainingOutboxHandler` → `pushToSearch(trainingId)`
   → `SearchIndexer`. `log` (default, dev) just logs; `http` (`HttpSearchIndexer`) POSTs
   `{url}/v1/lists/{listId}/index` with `X-Internal-Service: backend` + `X-Internal-Token`,
   through the shared circuit breaker. The payload includes the training's opaque `model` string
   (so search embeds queries with the same model). Failures make the outbox retry with backoff;
   the search service also lazily reloads from `/internal/search/lists/{id}` anyway.
6. **Retention.** `TrainingRetentionSweeper` (daily) deletes finished runs that are not `in_use`
   older than `xeye.training.retention-days` (`TRAINING_RETENTION_DAYS`, 0 = keep), embeddings
   included (FK cascade). `SearchLogRetentionSweeper` does the same for `searches`
   (`SEARCH_LOG_RETENTION_DAYS`). Both delete in short batches.

**LLM enrichment cache (`elements.generated_description`).** The worker's LLM step costs seconds
per element, so its output is cached: it comes back in `generated_descriptions` (element id →
enrichment JSON) on the completion webhook, is stored on the element, and is sent *back* to the
worker in the next launch payload (`ElementPayload.generated_description`). `Element.changeText` /
`changeDescription` set it to null — the two inputs it was derived from — so a retrain only pays
the LLM for what actually changed. Never populate this field from anywhere else: the worker owns
its format.

Semantics decided here (adjust if the user wants otherwise):
- **`trained`** is list-wide: a retrain marks *all* the list's elements untrained, then trained on
  completion (a training recomputes embeddings for the whole list).
- **`in_use`** = the training whose model is active for the list (auto-set on completion; the
  user can switch via `POST /trainings/{id}/use` if it covers the list's current elements).
- Edits never launch anything: they just keep the single PENDING row alive; the user decides
  when (and with which embedding model) to launch, within the launch caps above.

## Search-service integration (the `search` module)

The search microservice keeps everything in RAM and treats this backend as the source of
truth. Three pieces, all in the `search` module:

- **Internal sync API** (`InternalSearchController`; `/internal/**` requires the shared
  `X-Internal-Token`, verified by `SharedSecretAuthenticationFilter`, same as the webhook; the
  production proxy does not expose it): `GET /internal/search/bootstrap?limit=` (first keyset
  page of api key **hashes** and of list metadata, with `apiKeysNextAfterId`/`listsNextAfterId`
  when more remain — continued via `GET /internal/search/api-keys|lists?afterId=&limit=` — plus
  the available embedding models, which search pre-warms at startup, and `userLimits`: per-user
  search rate limits set by an admin), `GET /internal/search/lists/{listId}` (elements + the
  in_use training's `embeddingsData` (loaded from `training_embeddings`)/`model` — the lazy-load
  counterpart of the index push), `POST /internal/search/logs` (batched, bean-validated
  search-log ingestion → `searches` table, migration V2; ≤ 500 entries per batch).
- **Console playground proxy** (`ConsoleSearchController`, `POST /lists/{listId}/search`, JWT):
  the browser never holds an API key. `ConsoleSearchService` checks ownership, then the outbound
  port `SearchQueryGateway` (`HttpSearchQueryGateway` → search's internal
  `POST /v1/lists/{id}/search`; `UnavailableSearchQueryGateway` → 503 with provider `log`) runs
  the query. Private lists are allowed here; the public search API (API keys) only serves public
  lists and there is no client-side `allow_private` any more. Search's 404/429 map to
  `NotFoundException`/`TooManyRequestsException` (Retry-After kept), anything else to
  `ServiceUnavailableException` (503). Search's `degraded`/`degradation_reasons` are passed
  through unchanged (`ConsoleSearchResponse.degraded`); the console shows them as a warning.
- **Search rate limit is per user** (`users.search_rate_limit_per_minute`, migration V8, null =
  search's default): all of a user's API keys and their console searches share the quota. Only
  admins change it (`PUT /admin/users/{id}` with `searchRateLimitPerMinute`/`resetSearchRateLimit`),
  which publishes `UserSearchLimitChangedEvent` → `PUT /v1/users/{id}/limits` on search.
- **Change notifications** (`SearchSyncOutboxHandler`, fed by the outbox — delivered after
  commit with retries; a failure only means brief staleness): `ListMetaChangedEvent`
  (rename/visibility), `ListDeletedEvent`, `ListElementsChangedEvent` (any element mutation,
  **including params-only edits**, → cache invalidation on the search side),
  `ApiKeyCreatedEvent`/`ApiKeyDeletedEvent`, `UserDeletedEvent`, `UserSearchLimitChangedEvent` — all in
  `shared/event`, all `DomainEvent`s.
  Outbound port `SearchSyncNotifier`; impls `HttpSearchSyncNotifier` (provider `http`, through the
  shared circuit breaker) / `LoggingSearchSyncNotifier` (provider `log`, default).
- **Search logs**: domain `SearchLog`, in-port `SearchLogUseCases`, user endpoint
  `GET /lists/{listId}/searches` (owner-scoped, paginated), daily retention sweep.

Cross-module reads use internal in-ports: `ApiKeyQueryPort.findAfterId`, `ListQueryPort.findAfterId`,
`ElementQueryPort.findByListId`/`countByListId`, `TrainingQueryPort.findInUseByListId`/
`findEmbeddingsData`, `UserQueryPort.findSearchRateLimits`.

## Observability (metrics, logs, request id)

- **Metrics:** `micrometer-registry-prometheus` → `GET /actuator/prometheus` (permitted in
  `SecurityConfig`; the production proxy 404s every `/actuator/*` except health, so only the
  docker-network Prometheus of `xeye-infra` reaches it). Own meters: `MicrometerTrainingMetrics`
  (`xeye.trainings` counter tagged `outcome` = completed/failed/stalled/launch_failed, fed through
  the application port `TrainingMetrics` from the webhook controller, the dispatcher and the stalled
  sweeper; gauges `xeye.trainings.{queued,pending,launched}` read from `TrainingRepository` on each
  scrape) and `OutboxMetrics` (`xeye.outbox.{pending,failed}`). New meters: prefix `xeye.`, and keep
  gauge readers cheap (they run on every scrape).
- **Request id:** `RequestIdFilter` (first filter, `WebConfig`) puts `X-Request-Id` (the proxy's
  if sane, else a new one) in the MDC as `requestId` and echoes it in the response; the console
  pattern prints it (`logging.pattern.correlation`) and `OutboxRelay` sets `outbox-<id>` while
  delivering. `HttpSearchQueryGateway` forwards it to the search service (same id in both logs).
- **Structured logs:** profile `prod` logs JSON (`logging.structured.format.console`, ECS by
  default, `LOG_STRUCTURED_FORMAT` env; empty = text). Never log secrets: see `CONFIG.md`.
- **Callback host:** `xeye.training.callback-base-url` = `TRAINING_CALLBACK_BASE_URL` or
  `BACKEND_URL`; `xeye.backend.public-url` (= `BACKEND_URL`) is what the SSO redirect uses. In
  production the worker calls `hooks.xeye.es` (DNS-only, no Cloudflare body cap) while the public
  API host sits behind Cloudflare.

## Common commands

```bash
# Fully containerised dev stack (MariaDB + backend, ports 3307/8000):
docker compose -f docker-compose.dev.yml up --build
# apply code changes live (DevTools restarts the running app):
docker compose -f docker-compose.dev.yml exec backend mvn -o compile

# Or run the app on the host against a dockerised DB (best IDE hot-reload):
mvn spring-boot:run      # activates profile 'dev' (pom); defaults to the compose DB on localhost:3307 (DB_URL/DB_USERNAME/DB_PASSWORD override)

mvn -q compile           # compile only
mvn test                 # pure domain unit tests (no DB needed)
mvn -q -DskipTests package
```

Hot reload: DevTools watches `target/classes`. Saving a file in an IDE that auto-compiles
(VS Code Java, IntelliJ) triggers a restart; otherwise run `mvn compile`.

## Configuration (`application.yml`, all overridable by env var)

**Full reference with the "required in prod" column: `CONFIG.md`** (keep it in sync when adding a knob).
`ProductionConfigGuard` also rejects `localhost` anywhere in `CORS_ORIGINS`/`FRONTEND_URL`/`BACKEND_URL`/`SEARCH_SERVICE_URL`.

`xeye.jwt.{secret,expiration-minutes,issuer}`, `xeye.cors.allowed-origins`,
`xeye.training.{provider,webhook-secret,callback-base-url,mock-delay-ms,embedding-models,stalled-after-minutes,max-concurrent,max-concurrent-per-user,retention-days,docker.*,runpod.*}`,
`xeye.search.{provider,url,internal-service-name,internal-token,log-retention-days}` (`SearchProperties` lives in
`shared/config` — the `training` and `search` modules both use it), `xeye.http.*` (body limits),
`xeye.outbox.*` (relay), `spring.datasource.hikari.*` (pool; `DB_POOL_SIZE`),
`server.shutdown=graceful` + health probes (`/actuator/health/{liveness,readiness}`, readiness
includes the DB; the Dockerfile HEALTHCHECK uses readiness),
`xeye.auth.*` (`AuthProperties`: `FRONTEND_URL`, verification, admin emails, rate limits, captcha,
breach check, SSO), `xeye.email.*` (`EmailProperties` + `spring.mail.*` for `smtp`),
`DB_URL/DB_USERNAME/DB_PASSWORD`, `SERVER_PORT`, `SENTRY_DSN` (empty = off; Sentry Boot 4 starter).
Profile `dev` (must be activated explicitly — `mvn spring-boot:run` does it): mock training,
log search, verbose logs, seeds an admin user (`admin@xeye.local` / `admin1234`, see
`DevAdminSeeder`). Profile `prod`: no defaults, `ProductionConfigGuard`.

## API surface

Public: `GET /auth/config`, `POST /auth/{register,login,mfa,verify-email,resend-verification,
forgot-password,reset-password,sso/exchange}`, `GET /auth/sso/{provider}[/callback]`,
`POST /webhooks/training-update`, `/internal/search/*` (`X-Internal-Token`, search-service only).
Authenticated (`Authorization: Bearer <jwt>`):
`POST /auth/logout`, `POST /auth/logout-all` · `GET|PUT|DELETE /users/me`, `PUT /users/me/email`,
`PUT /users/me/password`, `POST /users/me/mfa/{setup,enable,disable}` ·
`GET /admin/users`, `GET|PUT|DELETE /admin/users/{id}`, `POST /admin/users/{id}/logout-all` (admin) ·
`GET|POST /api-keys`, `PUT|DELETE /api-keys/{id}` ·
(`POST /api-keys` is the only response carrying the raw key) ·
`GET|POST /lists` (`?offset&limit&q&public`), `GET|PUT|DELETE /lists/{id}`, `POST /lists/{listId}/search` (console playground) ·
`GET|POST /lists/{listId}/elements` (`?offset&limit&q`), `POST /lists/{listId}/elements/import`, `PUT|DELETE /elements/{id}` ·
`GET /lists/{listId}/trainings`, `POST /lists/{listId}/trainings` (retrain), `GET /trainings/{id}`,
`GET /trainings/pending`, `GET /trainings/embedding-models`, `POST /trainings/{id}/launch`,
`POST /trainings/{id}/use` · `GET /lists/{listId}/searches`.

## Where to add things

| Task | Touch |
|---|---|
| New endpoint | controller in the module's `infrastructure/web` + method on its `*UseCases` in-port + service |
| New table/column | Flyway migration `V__*.sql` + JPA entity + domain model + mapper + repo port/adapter |
| New outbound event | record in `shared/event` implementing `DomainEvent` (+ `TYPE`) + a case in the module's `OutboxHandler` |
| New list endpoint | take `offset`/`limit` params → `Paging.of` → repository returns `Page` → `PageResponse.from` |
| New exception→HTTP code | `shared/web/GlobalExceptionHandler` + `shared/exception` |
| New config knob | a `@ConfigurationProperties` record (auto-scanned) + `application.yml` + row in `CONFIG.md` (+ `.env.example`) |
| Swap an integration | implement the outbound port (`TrainingLauncher`/`SearchIndexer`) + `@ConditionalOnProperty` |

## Sibling services (in the parent `XEYE/` workspace)

`../search-service` (**the** search microservice, rebuilt Python/FastAPI, :8002 — see its
README.md; `../XEYE-search-service` is the legacy version it replaces),
`../training-service` (**the** training worker, rebuilt: one container per training, runs on
docker/RunPod — see its README.md; `../XEYE-training-service` is the legacy version),
`../XEYE-frontend` (Vue, legacy), `../frontend` (Nuxt, current), `../XEYE-traefik`.
There is no Java test suite for HTTP flows; verify by running the app and driving the endpoints
(both Python services have their own pytest suites).
