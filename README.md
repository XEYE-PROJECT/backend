# XEYE Backend (Java)

Backend de XEYE reescrito en **Java + Spring Boot 4** con **arquitectura hexagonal** y
organizado en **módulos por modelo** (`user`, `apikey`, `list`, `element`, `training`).
Versión simplificada del backend original en Python (trabajo de universidad): sin billing,
sin refresh tokens ni verificación de email.

## Qué hace

- Registro / login de usuarios (JWT) y edición/borrado de la propia cuenta.
- Gestión de **claves API** vinculadas al usuario.
- Gestión de **listas** (nombre, descripción, pública) y sus **elementos** (texto, parámetros,
  descripción, descripción generada, entrenado).
- Al cambiar la **descripción de una lista** o el **texto/descripción de un elemento** (o
  crear/borrar elementos), los elementos pasan a `trained=0` y se lanza un **entrenamiento**;
  al terminar se marcan `trained=1`, se activa ese entrenamiento (`in_use`) y se **envían al
  microservicio de búsqueda**.

## Stack

Java 17 · Spring Boot 4.1 · MariaDB · Flyway · Spring Security + JWT (jjwt) · Maven.

## Requisitos

- Docker + Docker Compose, **o** JDK 17+ y Maven para ejecutar en el host.

## Arranque rápido

### Opción A — todo en Docker (con hot-reload)

```bash
cp .env.example .env        # ajusta si quieres
docker compose -f docker-compose.dev.yml up --build
# Backend en http://localhost:8080 · MariaDB en localhost:3307
```

Para aplicar cambios de código sin reiniciar a mano (DevTools reinicia la app dentro del
contenedor tras recompilar):

```bash
docker compose -f docker-compose.dev.yml exec backend mvn -o compile
```

### Opción B — app en el host, base de datos en Docker (mejor hot-reload con el IDE)

```bash
docker compose -f docker-compose.dev.yml up mariadb -d
DB_URL='jdbc:mysql://localhost:3307/xeye?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC' \
DB_USERNAME=xeye DB_PASSWORD=xeye mvn spring-boot:run
# Backend en http://localhost:8080
```

No hay perfil por defecto: `mvn spring-boot:run` activa `dev` (configurado en `pom.xml`),
`docker-compose.dev.yml` también, y la imagen de producción fija `prod` en su Dockerfile. Sin
perfil, la app no arranca (los secretos no tienen default). En el IDE: `SPRING_PROFILES_ACTIVE=dev`.

El perfil `dev` (`application-dev.yml`) usa el proveedor de entrenamiento **mock** (simula el
entrenamiento en memoria, sin RunPod) y **loguea** el envío a búsqueda, así que arranca sin
credenciales. Además crea un admin de desarrollo: `admin@xeye.local` / `admin1234`.

El perfil `prod` (`application-prod.yml`) exige por entorno todos los secretos y la BD, y
`ProductionConfigGuard` aborta el arranque si algún valor es de desarrollo, demasiado corto o
inseguro (`TRAINING_PROVIDER=mock`, `SEARCH_PROVIDER` ≠ `http`, orígenes CORS o `BACKEND_URL`
sin `https://`). El error nombra la variable de entorno.

## Variables de entorno

Referencia completa (todas las variables, con lo **obligatorio en producción** y lo que comprueba
`ProductionConfigGuard`): [CONFIG.md](CONFIG.md). Plantilla: [.env.example](.env.example). Las principales:

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` o `prod` (obligatorio; la imagen de producción ya lo fija) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a MariaDB |
| `JWT_SECRET` | Secreto HS256 (≥ 32 bytes) |
| `CORS_ORIGINS` | Orígenes exactos del frontend, separados por comas |
| `TRAINING_PROVIDER` | `mock` (solo dev), `docker` o `runpod` |
| `RUNPOD_API_KEY`, `RUNPOD_ENDPOINT_ID`, `BACKEND_URL` | Entrenamiento real |
| `TRAINING_WEBHOOK_SECRET` | Firma (HMAC) el token por entrenamiento que viaja en el job y que el worker devuelve en `X-Webhook-Token`; el secreto no sale del backend |
| `SEARCH_PROVIDER` | `log` (solo dev) o `http` |
| `SEARCH_SERVICE_URL`, `SEARCH_INTERNAL_TOKEN` | URL y secreto compartido (`X-Internal-Token`) del microservicio de búsqueda |
| `SENTRY_DSN`, `SENTRY_ENVIRONMENT` | Error tracking (vacío = desactivado) |
| `FRONTEND_URL` | URL pública de la consola (enlaces de los emails, retorno del SSO) |
| `EMAIL_PROVIDER`, `EMAIL_FROM`, `EMAIL_REPLY_TO` | `log` (dev), `smtp` (IONOS: `SMTP_HOST/PORT/USERNAME/PASSWORD`) o `resend` (`RESEND_API_KEY`) |
| `AUTH_REQUIRE_EMAIL_VERIFICATION`, `ADMIN_EMAILS` | Verificación obligatoria (true) y emails que pasan a admin al entrar |
| `AUTH_RATE_*_PER_MINUTE` | Rate limit por IP de `/auth/*` |
| `PASSWORD_BREACH_CHECK` | `hibp` (prod) o `none` |
| `CAPTCHA_PROVIDER`, `CAPTCHA_SITE_KEY`, `CAPTCHA_SECRET` | CAPTCHA opcional (Cloudflare Turnstile) |
| `SSO_GOOGLE_*`, `SSO_MICROSOFT_*` | SSO OIDC; un proveedor se activa con client id + secret |

## Endpoints

Públicos (`/auth/*`, con rate limit por IP y respuestas idénticas exista o no la cuenta):

```
GET  /auth/config                 qué mostrar (verificación obligatoria, proveedores SSO, CAPTCHA)
POST /auth/register               202 siempre; envía el email de verificación (no abre sesión)
POST /auth/login                  {token,user} o {mfaRequired:true,mfaToken}; 403 EMAIL_NOT_VERIFIED,
                                  429 ACCOUNT_LOCKED tras 5 fallos (bloqueo 1,2,4… min)
POST /auth/mfa                    {mfaToken, code}: código TOTP o de recuperación -> sesión
POST /auth/verify-email           {token} del enlace (registro o cambio de email) -> sesión
POST /auth/resend-verification    {email} -> 202
POST /auth/forgot-password        {email} -> 202
POST /auth/reset-password         {token, password}; cierra todas las sesiones
GET  /auth/sso/{google|microsoft} redirige al proveedor; vuelve a /auth/sso/{p}/callback y de ahí a
                                  {FRONTEND_URL}/sso/callback?code=…; POST /auth/sso/exchange {token:code}
POST /auth/logout, /auth/logout-all   (con JWT) revoca este token / todas las sesiones
GET  /actuator/health
```

Servidor a servidor (secreto compartido en cabecera, comparado en tiempo constante, 403 si
falta o no coincide): `POST /webhooks/training-update` (`X-Webhook-Token` = token por entrenamiento
`<id>.<hmac>` que el backend metió en el job; solo puede reportar sobre ese run)
y `/internal/search/*` (`X-Internal-Token`, search-service; el proxy de producción no los
publica). El resto requiere cabecera `Authorization: Bearer <token>` (JWT de 60 min con `jti`
revocable y versión de sesión: cambiar contraseña/email, activar 2FA o "cerrar todas las
sesiones" invalida los anteriores):

```
GET|PUT|DELETE /users/me          (PUT: name, surname, locale)
PUT /users/me/email               {email, currentPassword}: enlace al nuevo buzón; se aplica al confirmar
PUT /users/me/password            {currentPassword, newPassword} -> token nuevo (los demás se cierran)
POST /users/me/mfa/setup|enable|disable
GET /admin/users, GET|PUT|DELETE /admin/users/{id}, POST /admin/users/{id}/logout-all   (ROLE_ADMIN;
                                  PUT admite searchRateLimitPerMinute / resetSearchRateLimit: cupo de
                                  búsquedas/min de la cuenta, compartido por todas sus API keys)
GET|POST /api-keys        PUT|DELETE /api-keys/{id}   (POST es la ÚNICA respuesta con la clave completa;
                                                      después solo existe su hash y se muestra el prefijo)
GET|POST /lists           GET|PUT|DELETE /lists/{id}   (GET /lists admite ?q= y ?public=; cada lista trae elementCount;
                                                       llmEnrichment=false = opt-out del LLM: se entrena sin descripciones IA)
POST /lists/{listId}/search          playground de la consola: {searchTerm, limit?, includeScoreBreakdown?}
                                     -> el backend reenvía al buscador por la red interna (también
                                     listas privadas; la API key nunca pasa por el navegador); la
                                     respuesta trae degraded/degradationReasons tal como los da el buscador
GET|POST /lists/{listId}/elements    PUT|DELETE /elements/{id}   (GET admite ?q=; POST …/elements/import por lotes)
GET /lists/{listId}/trainings        GET /trainings/{id}, POST /lists/{listId}/trainings (encola), /trainings/{id}/launch|use
GET /lists/{listId}/searches         historial de búsquedas por API key
```

**Paginación.** Todos los listados (`/lists`, `/lists/{id}/elements`, `/lists/{id}/trainings`,
`/api-keys`, `/lists/{id}/searches`, `/admin/users`) aceptan `?offset=&limit=` (`limit` ≤ 200, 50 por
defecto) y responden `{items, total, offset, limit}`. La API interna del buscador pagina por clave
(`GET /internal/search/bootstrap?limit=` + `/internal/search/api-keys|lists?afterId=&limit=`).

**Errores.** Siempre JSON `{status, error, message, code, details?}` con un `code` de máquina:
`VALIDATION_FAILED` (400, con `details` por campo), `MALFORMED_BODY`, `INVALID_PARAMETER`,
`NOT_FOUND`, `METHOD_NOT_ALLOWED` (405 + `Allow`), `REQUEST_TOO_LARGE` (413; 1 MB por defecto,
16 MB en la importación, 256 MB en el webhook), `CONCURRENT_MODIFICATION` (409, bloqueo
optimista en listas/elementos/trainings), `DATA_CONFLICT` (409), `RATE_LIMITED` (429 +
`Retry-After`), `SERVICE_UNAVAILABLE` (503), `INTERNAL_ERROR` (500, sin detalles).

**Cola de entrenamientos.** Lanzar un training lo pone en `queued`; el despachador arranca los
runs cuando hay hueco (`TRAINING_MAX_CONCURRENT` en total, `TRAINING_MAX_CONCURRENT_PER_USER` por
usuario, con equidad entre usuarios). El webhook del worker es idempotente (duplicados y
callbacks fuera de orden responden 200 con `applied: false`) y cada callback renueva el latido
(`last_heartbeat_at`) que vigila el barrido de estancados. Los eventos hacia el buscador y hacia
el flujo de training salen por un outbox transaccional (`outbox_events`) con reintentos.

Ejemplo:

```bash
# Registro (202) -> el enlace de verificación sale en el log (EMAIL_PROVIDER=log) -> verificar abre sesión
curl -s -X POST localhost:8000/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Joan","surname":"M","email":"joan@test.com","password":"correct horse battery"}'
TOKEN=$(curl -s -X POST localhost:8000/auth/verify-email -H 'Content-Type: application/json' \
  -d '{"token":"<token del enlace del log>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s -X POST localhost:8080/lists -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Productos","description":"...","public":true}'
```

## Tests, estilo y CI

```bash
mvn test              # checkstyle + tests unitarios (dominio, JWT, TOTP, rate limiter, guard de prod, contratos; sin BD)
mvn verify            # lo anterior + tests de integración (*IT.java): MariaDB real vía Testcontainers (necesita docker)
mvn checkstyle:check  # solo el estilo (checkstyle.xml: imports, llaves, espacios, líneas ≤ 140)
```

- **Integración** (`src/test/java/.../it`): la app entera con perfil `dev` sobre `mariadb:10.11`,
  migraciones de Flyway aplicadas, HTTP real: seguridad (JWT, admin, secretos de servicio),
  errores (`GlobalExceptionHandler`), esquema y el webhook con los payloads reales del worker.
- **Contratos** (`src/test/resources/contracts/`, copia canónica): un JSON de ejemplo por mensaje
  entre servicios (job al worker, webhook, push de índice, bootstrap, datos de lista). Aquí se
  comprueba que los records producen/aceptan exactamente ese JSON; el buscador y el worker tienen
  copias idénticas y sus propios tests (`bash contracts-check.sh` en `xeye-infra` verifica que
  las tres copias coinciden).
- **CI** (`.github/workflows/`): `ci.yml` en cada pull request ejecuta `checks.yml` (gitleaks +
  `mvn verify`); `deploy.yml` en cada push a master hace lo mismo y después imagen `:sha`,
  Trivy y despliegue; `release.yml` en cada tag `vX.Y.Z` publica la imagen `:vX.Y.Z` y la GitHub
  Release con las notas del `CHANGELOG.md`. La protección de `master` (PR + checks en verde) se
  importa desde `xeye-infra/github/ruleset-master.json`.
- **Versionar**: anota los cambios en `CHANGELOG.md` ("Unreleased") y publica con
  `bash release.sh X.Y.Z` (mueve la sección, fija la versión del `pom.xml`, commit + tag; después
  `git push origin master --tags`). Desplegar una versión concreta: `deploy.sh xeye-backend vX.Y.Z`.

## Estructura

```
src/main/java/com/xeye/backend/
├── shared/         seguridad JWT, manejo de errores, eventos, config async
├── user/           usuarios + autenticación
├── apikey/         claves API
├── list/           listas
├── element/        elementos de lista
└── training/       entrenamientos (mock/runpod), webhook, envío a búsqueda
```

Cada módulo se divide en `domain/` (modelo puro), `application/` (puertos + servicios) e
`infrastructure/` (JPA, web, adaptadores externos). Ver [CLAUDE.md](CLAUDE.md) para los detalles
de arquitectura y las particularidades de Spring Boot 4.
```
