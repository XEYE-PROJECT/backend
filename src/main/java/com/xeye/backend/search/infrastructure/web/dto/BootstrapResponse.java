package com.xeye.backend.search.infrastructure.web.dto;

import java.util.List;

/**
 * Snapshot de arranque para el microservicio de búsqueda: todas las api keys (en claro), los
 * metadatos de todas las listas y los modelos de embedding (búsqueda los precalienta al arrancar).
 */
public record BootstrapResponse(List<ApiKeyEntry> apiKeys, List<ListEntry> lists, List<String> embeddingModels) {

    public record ApiKeyEntry(Long id, Long userId, String apiKey) {
    }

    public record ListEntry(Long id, Long userId, String name, boolean isPublic) {
    }
}
