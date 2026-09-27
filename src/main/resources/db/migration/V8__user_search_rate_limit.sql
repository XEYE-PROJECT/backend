-- Límite de búsquedas por minuto de cada usuario (sección C del backlog). NULL = el valor por
-- defecto del search-service (RATE_LIMIT_PER_MINUTE); solo un administrador lo cambia. Todas
-- las API keys del usuario (y sus búsquedas desde la consola) comparten ese cupo: el buscador
-- lo recibe en el bootstrap y por PUT /v1/users/{id}/limits.
ALTER TABLE users
    ADD COLUMN search_rate_limit_per_minute INT NULL AFTER locale;
