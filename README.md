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

El perfil `dev` (por defecto) usa el proveedor de entrenamiento **mock** (simula el
entrenamiento en memoria, sin RunPod) y **loguea** el envío a búsqueda, así que arranca sin
credenciales. Además crea un admin de desarrollo: `admin@xeye.local` / `admin1234`.

## Variables de entorno

Ver [.env.example](.env.example). Las principales:

| Variable | Descripción |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a MariaDB |
| `JWT_SECRET` | Secreto HS256 (≥ 32 bytes) |
| `TRAINING_PROVIDER` | `mock` (dev) o `runpod` |
| `RUNPOD_API_KEY`, `RUNPOD_ENDPOINT_ID`, `TRAINING_WEBHOOK_SECRET`, `BACKEND_URL` | Entrenamiento real |
| `SEARCH_PROVIDER` | `log` (dev) o `http` |
| `SEARCH_SERVICE_URL` | URL del microservicio de búsqueda |

## Endpoints

Públicos: `POST /auth/register`, `POST /auth/login`, `POST /webhooks/training-update`.
El resto requiere cabecera `Authorization: Bearer <token>`:

```
GET|PUT|DELETE /users/me
GET|POST /api-keys        PUT|DELETE /api-keys/{id}
GET|POST /lists           GET|PUT|DELETE /lists/{id}
GET|POST /lists/{listId}/elements    PUT|DELETE /elements/{id}
GET /lists/{listId}/trainings        GET /trainings/{id}
```

Ejemplo:

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Joan","surname":"M","email":"joan@test.com","password":"password123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s -X POST localhost:8080/lists -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Productos","description":"...","public":true}'
```

## Tests

```bash
mvn test     # tests unitarios de dominio (no necesitan base de datos)
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
