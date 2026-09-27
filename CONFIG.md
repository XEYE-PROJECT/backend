# Referencia de configuración — backend XEYE

Todas las variables de entorno que lee el backend, con lo que es **obligatorio en producción**.
Los defaults de desarrollo viven en `application-dev.yml` (perfil `dev`); en `prod` no hay
defaults para nada marcado "obligatorio": un placeholder sin variable aborta el arranque
(`Could not resolve placeholder`) y `ProductionConfigGuard` rechaza además valores de
desarrollo, secretos cortos, providers `mock`/`log`, URLs `http://` y cualquier `localhost`.

Leyenda: **Prod** = obligatorio en producción · 🔑 = secreto (nunca en git, nunca en logs) ·
Guard = lo comprueba `ProductionConfigGuard` al arrancar con el perfil `prod`.

## Runtime

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` o `prod`. Sin perfil la app no arranca; la imagen fija `prod` | `dev` (pom/compose) | **prod** | — |
| `SERVER_PORT` | Puerto HTTP | `8000` | opcional | — |
| `JAVA_TOOL_OPTIONS` | Heap etc. (`-Xmx320m` en el VPS de 4 GB) | — | recomendado | — |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | Pool JDBC | 10 | recomendado (`5`) | — |

## Base de datos

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `DB_URL` | JDBC MariaDB (`jdbc:mysql://xeye-db:3306/xeye?...`) | compose `localhost:3307` | **sí** | — |
| `DB_USERNAME` | Usuario | `xeye` | **sí** | — |
| `DB_PASSWORD` 🔑 | Contraseña | `xeye` | **sí** | no vacía, no `xeye`/`rootpassword` |

## Seguridad y sesiones

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `JWT_SECRET` 🔑 | HS256, ≥ 32 bytes (`openssl rand -base64 48`) | valor dev inseguro | **sí** | ≥ 32 bytes, no dev |
| `JWT_EXPIRATION_MINUTES` | Vida del access token | `60` | opcional | — |
| `CORS_ORIGINS` | Orígenes exactos de la consola, separados por comas | localhost:3000… | **sí** | solo `https://`, sin localhost |

## Cuentas y autenticación (`xeye.auth.*`)

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `FRONTEND_URL` | URL pública de la consola (enlaces de email, retorno SSO) | `http://localhost:3000` | **sí** | `https://`, sin localhost |
| `AUTH_REQUIRE_EMAIL_VERIFICATION` | Exigir email verificado para entrar | `true` | `true` | — |
| `ADMIN_EMAILS` | Emails que pasan a admin al iniciar sesión (bootstrap) | vacío | recomendado | — |
| `AUTH_RATE_LOGIN_PER_MINUTE` | Rate limit por IP de `POST /auth/login` (≤ 0 desactiva) | `10` | opcional | — |
| `AUTH_RATE_REGISTER_PER_MINUTE` | Idem registro / reenvío de verificación | `5` | opcional | — |
| `AUTH_RATE_PASSWORD_RESET_PER_MINUTE` | Idem olvido / reset / verify-email / sso exchange | `5` | opcional | — |
| `PASSWORD_BREACH_CHECK` | `hibp` (Have I Been Pwned, k-anonimato) o `none` | `none` | `hibp` | — |
| `MFA_ISSUER` | Nombre en las apps TOTP | `XEYE` | opcional | — |
| `MFA_TRUST_DAYS` | Días sin repetir el 2FA en un dispositivo recordado (0 = siempre) | `30` | opcional | — |
| `CAPTCHA_PROVIDER` | `none` o `turnstile` | `none` | opcional | — |
| `CAPTCHA_SITE_KEY` | Site key pública de Turnstile (la consola la carga de `/auth/config`) | vacío | si turnstile | — |
| `CAPTCHA_SECRET` 🔑 | Secret de Turnstile | vacío | si turnstile | — |
| `SSO_GOOGLE_CLIENT_ID` / `SSO_GOOGLE_CLIENT_SECRET` 🔑 | OIDC Google; redirect `{BACKEND_URL}/auth/sso/google/callback` | vacío | opcional | — |
| `SSO_MICROSOFT_CLIENT_ID` / `SSO_MICROSOFT_CLIENT_SECRET` 🔑 / `SSO_MICROSOFT_TENANT` | OIDC Microsoft (`common` por defecto) | vacío | opcional | — |

## Email transaccional (`xeye.email.*`, `spring.mail.*`)

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `EMAIL_PROVIDER` | `log` (imprime el email), `smtp` (IONOS) o `resend` | `log` | **smtp** o **resend** | `log` prohibido con verificación activa |
| `EMAIL_FROM` | Remitente autorizado por el proveedor | `XEYE <noreply@xeye.es>` | recomendado | — |
| `EMAIL_REPLY_TO` | Buzón de respuestas | `info@xeye.es` | recomendado | — |
| `SMTP_HOST` / `SMTP_PORT` | IONOS: `smtp.ionos.es` / `587` (STARTTLS) | IONOS | si smtp | — |
| `SMTP_USERNAME` | Buzón completo (`noreply@xeye.es`) | vacío | si smtp | no vacío |
| `SMTP_PASSWORD` 🔑 | Contraseña del buzón (entre comillas simples en `.env` si lleva `$`/`#`) | vacío | si smtp | no vacío |
| `RESEND_API_KEY` 🔑 | API key de Resend | vacío | si resend | no vacío |

## Entrenamientos (`xeye.training.*`)

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `TRAINING_PROVIDER` | `mock` (solo dev), `docker` o `runpod` | `mock` | **docker** o **runpod** | `mock` prohibido |
| `TRAINING_WEBHOOK_SECRET` 🔑 | `X-Webhook-Token` del callback; el worker lo recibe por su entorno (`WEBHOOK_SECRET`) | `dev-webhook-secret` | **sí** | ≥ 32 chars, no dev |
| `BACKEND_URL` | URL pública de este backend a la que el worker llama de vuelta | `http://localhost:8000` | **sí** | `https://`, sin localhost |
| `TRAINING_EMBEDDING_MODELS` | Modelos ofrecidos (el primero = por defecto); worker y search deben poder cargarlos | MiniLM,mpnet | recomendado | — |
| `TRAINING_MAX_CONCURRENT` | Trainings a la vez en todo el backend (≤ 0 = sin límite) | `1` | opcional | — |
| `TRAINING_STALLED_AFTER_MINUTES` | Minutos sin webhook antes de marcar fallido (0 desactiva) | `30` | opcional | — |
| `TRAINING_FIXED_PRICE` / `TRAINING_PRICE_PER_DESCRIPTION` | Precio mostrado (EUR) | `0.3` / `0.0053` | opcional | — |
| `TRAINING_MOCK_DELAY_MS` | Solo provider mock | `500` | — | — |
| `RUNPOD_API_KEY` 🔑 / `RUNPOD_ENDPOINT_ID` / `RUNPOD_TIMEOUT_SECONDS` | Provider runpod | vacío | si runpod | — |
| `TRAINING_DOCKER_IMAGE` / `_NETWORK` / `_GPUS` / `_BINARY` | Provider docker | ver `.env.example` | si docker | — |
| `TRAINING_INPUT_DIR` / `TRAINING_HOST_INPUT_DIR` | Dónde se escribe el JSON del job y cómo lo ve el daemon | `/tmp/xeye-training` | si docker | — |
| `TRAINING_DOCKER_ENV` 🔑 | `KEY=VALUE,...` extra para el worker (puede llevar claves de LLM; se redacta en los logs) | vacío | opcional | — |

## Buscador (`xeye.search.*`)

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `SEARCH_PROVIDER` | `log` (solo dev) o `http` | `log` | **http** | ≠ `http` prohibido |
| `SEARCH_SERVICE_URL` | URL del search-service por la red docker (`http://search-service:8002`) | `http://localhost:8002` | **sí** | sin localhost |
| `SEARCH_INTERNAL_TOKEN` 🔑 | Secreto compartido `X-Internal-Token` (= `INTERNAL_TOKEN` en search) | `dev-internal-token` | **sí** | ≥ 32 chars, no dev |

## Observabilidad

| Variable | Descripción | Default dev | Prod | Guard |
|---|---|---|---|---|
| `SENTRY_DSN` | DSN del proyecto `xeye-backend` (vacío = desactivado) | vacío | recomendado | — |
| `SENTRY_ENVIRONMENT` | Etiqueta de entorno | `local` | `production` | — |
| `SENTRY_RELEASE` | Commit desplegado; lo fija el Dockerfile (`GIT_SHA`) | vacío | automático | — |

## Qué NO sale nunca en los logs

- Contraseña del admin de desarrollo (`DevAdminSeeder` solo loguea el email).
- Los `-e KEY=VALUE` de `docker run` del provider docker (`DockerTrainingLauncher` los redacta a `KEY=***`).
- Cabeceras `Authorization`, `X-Webhook-Token`, `X-Internal-Token` en los eventos de Sentry (`SentryConfig`).
- Emails de usuario en el log de auditoría `xeye.audit` (enmascarados `j***@dominio`).
- `/actuator/env` no está expuesto (solo `health`, sin detalles) y el proxy bloquea el resto de `/actuator/*`.

## Comprobación rápida

```bash
# Sin perfil ni variables: falla nombrando el placeholder que falta.
docker run --rm -e SPRING_PROFILES_ACTIVE=prod ghcr.io/xeye-project/backend:latest
# Con un env de producción completo (xeye-infra/env/xeye.env):
docker run --rm --env-file env/xeye.env ghcr.io/xeye-project/backend:latest
# -> "Production configuration verified" o "Unsafe production configuration: - <VARIABLE> ..."
```
