package com.xeye.backend.search.infrastructure.web.dto;

import java.util.List;

/**
 * Snapshot de arranque para el microservicio de búsqueda: la primera página de hashes SHA-256
 * de api keys (nunca su valor) y de metadatos de listas — con {@code apiKeysNextAfterId} /
 * {@code listsNextAfterId} no nulos si quedan más, que se recorren con
 * {@code GET /internal/search/api-keys?afterId=} y {@code GET /internal/search/lists?afterId=} —,
 * los modelos de embedding (búsqueda los precalienta al arrancar) y los cupos de búsqueda por
 * usuario distintos del por defecto (pocos: los fija un admin).
 */
public record BootstrapResponse(List<ApiKeyEntry> apiKeys, Long apiKeysNextAfterId,
                                List<ListEntry> lists, Long listsNextAfterId,
                                List<String> embeddingModels, List<UserLimitEntry> userLimits) {

    public record ApiKeyEntry(Long id, Long userId, String keyHash) {
    }

    public record ListEntry(Long id, Long userId, String name, boolean isPublic) {
    }

    /** Búsquedas/minuto fijadas por un admin (todas las keys del usuario comparten el cupo). */
    public record UserLimitEntry(Long userId, int rateLimitPerMinute) {
    }

    /** Una página por clave de cualquiera de las dos colecciones: {@code nextAfterId} null = última. */
    public record KeysetPage<T>(List<T> items, Long nextAfterId) {
    }
}
