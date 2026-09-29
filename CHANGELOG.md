# Changelog

Formato [Keep a Changelog](https://keepachangelog.com/es/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).
Las entradas nuevas van en "Unreleased"; `bash release.sh X.Y.Z` las convierte en una versión y
crea el tag que publica la imagen `ghcr.io/xeye-project/backend:vX.Y.Z` y la GitHub Release.

## [Unreleased]

### Añadido
- Reconstrucción del backend en Java 17 / Spring Boot 4 como monolito modular hexagonal
  (usuarios, api keys, listas, elementos, trainings, búsqueda) sobre MariaDB con Flyway.
- Cuentas: verificación de email, recuperación de contraseña, 2FA TOTP con códigos de recuperación,
  SSO OIDC (Google/Microsoft), bloqueo progresivo, revocación de sesiones, auditoría de seguridad.
- API keys hasheadas (mostradas una sola vez), cupo de búsquedas por usuario, playground de la
  consola a través del backend.
- Cola de entrenamientos con caps por usuario, latido y barrido de estancados, outbox transaccional
  hacia el buscador y el worker, paginación y límites de cuerpo en toda la API.
- Perfil `prod` sin defaults con guard de arranque, secretos de servicio en la capa de seguridad,
  métricas Prometheus, request id, logs JSON, Sentry.
- CI: gitleaks, checkstyle, tests unitarios y de integración (Testcontainers), tests de contrato
  con el buscador y el worker, imagen escaneada con Trivy, despliegue por SHA con rollback.
- Datos hacia el LLM (sección D): opt-out por lista (`llmEnrichment` en `POST/PUT /lists`,
  migración V10): esas listas se entrenan siempre sin descripciones IA y el job lleva
  `list.llm_enrichment=false`; coste real del worker (`cost.llm`, tokens) guardado en el training.
- Webhook del worker autenticado con un token **por entrenamiento** (`webhook_token` en el job,
  HMAC-SHA256 del secreto): el secreto ya no sale del backend (ni al fichero del job ni a RunPod)
  y un token solo puede reportar sobre su run (`WEBHOOK_TOKEN_MISMATCH` si no coincide).
- Provider docker: fichero del job con permisos 600 en `~/.xeye/training-jobs` (fuera de `/tmp`),
  cedido al uid del worker, borrado en el primer callback, al fallar el lanzamiento o al marcar
  el run estancado; ya no pasa `WEBHOOK_SECRET` al contenedor.

### Cambiado
- `X-Webhook-Token` ya no acepta el secreto en claro: solo tokens por entrenamiento.

[Unreleased]: https://github.com/XEYE-PROJECT/backend/compare/master...HEAD
