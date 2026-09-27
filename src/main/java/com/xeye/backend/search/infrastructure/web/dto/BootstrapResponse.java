package com.xeye.backend.search.infrastructure.web.dto;

import java.util.List;

/**
 * Snapshot de arranque para el microservicio de búsqueda: los hashes SHA-256 de todas las api
 * keys (nunca su valor), los metadatos de todas las listas, los modelos de embedding (búsqueda
 * los precalienta al arrancar) y los cupos de búsqueda por usuario distintos del por defecto.
 */
public record BootstrapResponse(List<ApiKeyEntry> apiKeys, List<ListEntry> lists, List<String> embeddingModels,
                                List<UserLimitEntry> userLimits) {

    public record ApiKeyEntry(Long id, Long userId, String keyHash) {
    }

    public record ListEntry(Long id, Long userId, String name, boolean isPublic) {
    }

    /** Búsquedas/minuto fijadas por un admin (todas las keys del usuario comparten el cupo). */
    public record UserLimitEntry(Long userId, int rateLimitPerMinute) {
    }
}
