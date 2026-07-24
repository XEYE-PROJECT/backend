# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

XEYE backend, **rebuilt in Java** (was FastAPI/Python at `../XEYE-backend`). It is a
**modular monolith with hexagonal architecture**: one module per aggregate
(`user`, `apikey`, `list`, `element`, `training`, `search`) plus a `shared` module. Each module
has its own `domain` → `application` → `infrastructure` layers (dependencies point inward only).
The `search` module owns the integration with the rebuilt search microservice
(`../search-service`): the `searches` log table, the `/internal/search/*` sync API, and the
change notifications pushed to it (see "Search-service integration" below).

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
- **Errors:** throw `shared.exception.*` (`NotFoundException`→404, `ConflictException`→409,
  `BadRequestException`→400, `UnauthorizedException`→401, `ForbiddenException`→403).
  `GlobalExceptionHandler` maps them to `ApiError` JSON. Do not catch them in controllers.
- **DB-managed timestamps:** JPA entities use `@Generated` + `insertable=false, updatable=false`
  on `created_at`/`updated_at`; the DB defaults / `ON UPDATE` manage them.

## The training flow (the crux)

Trigger → launch → callback → activate. All wiring lives in the `training` module.

1. **Trigger.** `ListService.update` (when the description changes) and `ElementService`
   (element created / text|description changed / deleted) publish `TrainingRequestedEvent`.
2. **Launch.** `TrainingEventListener` handles it **`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`**
   (off the request thread, after the edit commits). It calls `TrainingService.prepareLaunch`
   (creates a `Training` row `QUEUED`, marks **all** the list's elements `trained=false`, builds
   the payload and records the launch-time element ids on `trainings.element_ids` — the search
   service aligns embedding rows to elements by id), then `TrainingLauncher.launch(...)`,
   then `markLaunched`.
3. **Provider** (`xeye.training.provider`). All four send the *same* job payload
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
   - `lambda`: `LambdaTrainingLauncher` invokes an AWS Lambda container function **asynchronously**
     (`InvocationType=Event`), so no request thread waits on the 15-min execution wall.
   - `runpod`: `RunPodTrainingLauncher` POSTs `https://api.runpod.ai/v2/{endpointId}/run`.
4. **Callback.** `POST /webhooks/training-update` (`TrainingWebhookController`, `X-Webhook-Token`
   header) → `TrainingService.applyUpdate`. On `completed`: set embeddings/model/time/cost, cache the
   worker's `generated_descriptions` on the elements (see below), set this training
   **`in_use=true`** (clearing it on all other trainings of the list), mark the list's elements
   `trained=true`, and push to search via `SearchIndexer`.
5. **Search push** (`xeye.search.provider`): `log` (default, dev) just logs; `http`
   (`HttpSearchIndexer`) POSTs `{url}/v1/lists/{listId}/index` with `X-Internal-Service: backend`
   + `X-Internal-Token`. The payload includes the training's opaque `model` string (so search
   embeds queries with the same model) and is **non-fatal**: failures are caught and logged,
   because the search service lazily reloads from `/internal/search/lists/{id}` anyway.

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
- **`in_use`** = the one currently-active/most-recent completed training of a list.
- No debouncing: each qualifying edit launches a training.

## Search-service integration (the `search` module)

The search microservice keeps everything in RAM and treats this backend as the source of
truth. Three pieces, all in the `search` module:

- **Internal sync API** (`InternalSearchController`; route is `permitAll` and guarded by the
  shared `X-Internal-Token`, same pattern as the webhook): `GET /internal/search/bootstrap`
  (raw api keys + all list metadata + the available embedding models, which search
  pre-warms at startup), `GET /internal/search/lists/{listId}` (elements + the
  in_use training's `embeddingsData`/`model` — the lazy-load counterpart of the index push),
  `POST /internal/search/logs` (batched search-log ingestion → `searches` table, migration V2).
- **Change notifications** (`SearchSyncEventListener`, `@Async("searchSyncTaskExecutor")` +
  AFTER_COMMIT, best-effort — failures only mean brief staleness): `ListMetaChangedEvent`
  (rename/visibility), `ListDeletedEvent`, `ListElementsChangedEvent` (any element mutation,
  **including params-only edits**, → cache invalidation on the search side),
  `ApiKeyCreatedEvent`/`ApiKeyDeletedEvent`, `UserDeletedEvent` — all in `shared/event`.
  Outbound port `SearchSyncNotifier`; impls `HttpSearchSyncNotifier` (provider `http`) /
  `LoggingSearchSyncNotifier` (provider `log`, default).
- **Search logs**: domain `SearchLog`, in-port `SearchLogUseCases`, user endpoint
  `GET /lists/{listId}/searches` (owner-scoped, `?limit=` capped at 200).

Cross-module reads use internal in-ports: `ApiKeyQueryPort.findAll`, `ListQueryPort.findAll`,
`ElementQueryPort.findByListId`, `TrainingQueryPort.findInUseByListId`.

## Common commands

```bash
# Fully containerised dev stack (MariaDB + backend, ports 3307/8000):
docker compose -f docker-compose.dev.yml up --build
# apply code changes live (DevTools restarts the running app):
docker compose -f docker-compose.dev.yml exec backend mvn -o compile

# Or run the app on the host against a dockerised DB (best IDE hot-reload):
mvn spring-boot:run      # profile 'dev' by default; needs DB_URL/DB_USERNAME/DB_PASSWORD

mvn -q compile           # compile only
mvn test                 # pure domain unit tests (no DB needed)
mvn -q -DskipTests package
```

Hot reload: DevTools watches `target/classes`. Saving a file in an IDE that auto-compiles
(VS Code Java, IntelliJ) triggers a restart; otherwise run `mvn compile`.

## Configuration (`application.yml`, all overridable by env var)

`xeye.jwt.{secret,expiration-minutes,issuer}`, `xeye.cors.allowed-origins`,
`xeye.training.{provider,webhook-secret,callback-base-url,mock-delay-ms,docker.*,lambda.*,runpod.*}`,
`xeye.search.{provider,url,internal-service-name,internal-token}` (`SearchProperties` lives in
`shared/config` — the `training` and `search` modules both use it),
`DB_URL/DB_USERNAME/DB_PASSWORD`, `SERVER_PORT`.
Profile `dev` (default): mock training, log search, verbose logs, seeds an admin user
(`admin@xeye.local` / `admin1234`, see `DevAdminSeeder`).

## API surface

Public: `POST /auth/register`, `POST /auth/login`, `POST /webhooks/training-update`,
`/internal/search/*` (`X-Internal-Token`, search-service only).
Authenticated (`Authorization: Bearer <jwt>`):
`GET|PUT|DELETE /users/me` · `GET|POST /api-keys`, `PUT|DELETE /api-keys/{id}` ·
`GET|POST /lists`, `GET|PUT|DELETE /lists/{id}` ·
`GET|POST /lists/{listId}/elements`, `POST /lists/{listId}/elements/import`, `PUT|DELETE /elements/{id}` ·
`GET /lists/{listId}/trainings`, `GET /trainings/{id}` · `GET /lists/{listId}/searches`.

## Where to add things

| Task | Touch |
|---|---|
| New endpoint | controller in the module's `infrastructure/web` + method on its `*UseCases` in-port + service |
| New table/column | Flyway migration `V__*.sql` + JPA entity + domain model + mapper + repo port/adapter |
| New exception→HTTP code | `shared/web/GlobalExceptionHandler` + `shared/exception` |
| New config knob | a `@ConfigurationProperties` record (auto-scanned) + `application.yml` |
| Swap an integration | implement the outbound port (`TrainingLauncher`/`SearchIndexer`) + `@ConditionalOnProperty` |

## Sibling services (in the parent `XEYE/` workspace)

`../search-service` (**the** search microservice, rebuilt Python/FastAPI, :8002 — see its
README.md; `../XEYE-search-service` is the legacy version it replaces),
`../training-service` (**the** training worker, rebuilt: one container per training, runs on
docker/Lambda/RunPod/Batch — see its README.md; `../XEYE-training-service` is the legacy version),
`../XEYE-frontend` (Vue, legacy), `../frontend` (Nuxt, current), `../XEYE-traefik`.
There is no Java test suite for HTTP flows; verify by running the app and driving the endpoints
(both Python services have their own pytest suites).
