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

Ver [.env.example](.env.example). Las principales:

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` o `prod` (obligatorio; la imagen de producción ya lo fija) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a MariaDB |
| `JWT_SECRET` | Secreto HS256 (≥ 32 bytes) |
| `CORS_ORIGINS` | Orígenes exactos del frontend, separados por comas |
| `TRAINING_PROVIDER` | `mock` (solo dev), `docker` o `runpod` |
| `RUNPOD_API_KEY`, `RUNPOD_ENDPOINT_ID`, `BACKEND_URL` | Entrenamiento real |
| `TRAINING_WEBHOOK_SECRET` | Secreto de `X-Webhook-Token`; el worker lo recibe por su entorno (`WEBHOOK_SECRET`), nunca en el job |
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
falta o no coincide): `POST /webhooks/training-update` (`X-Webhook-Token`, worker de training)
y `/internal/search/*` (`X-Internal-Token`, search-service; el proxy de producción no los
publica). El resto requiere cabecera `Authorization: Bearer <token>` (JWT de 60 min con `jti`
revocable y versión de sesión: cambiar contraseña/email, activar 2FA o "cerrar todas las
sesiones" invalida los anteriores):

```
GET|PUT|DELETE /users/me          (PUT: name, surname, locale)
PUT /users/me/email               {email, currentPassword}: enlace al nuevo buzón; se aplica al confirmar
PUT /users/me/password            {currentPassword, newPassword} -> token nuevo (los demás se cierran)
POST /users/me/mfa/setup|enable|disable
GET /admin/users, GET|PUT|DELETE /admin/users/{id}, POST /admin/users/{id}/logout-all   (ROLE_ADMIN)
GET|POST /api-keys        PUT|DELETE /api-keys/{id}   (POST es la ÚNICA respuesta con la clave completa;
                                                      después solo existe su hash y se muestra el prefijo)
GET|POST /lists           GET|PUT|DELETE /lists/{id}
GET|POST /lists/{listId}/elements    PUT|DELETE /elements/{id}
GET /lists/{listId}/trainings        GET /trainings/{id}
```

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

## Tests

```bash
mvn test     # tests unitarios (dominio, política de contraseñas, TOTP, JWT, rate limiter, guard de producción; sin BD)
```

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
