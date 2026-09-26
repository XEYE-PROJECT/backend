package com.xeye.backend.search.infrastructure.web.dto;

import java.util.List;

/**
 * Snapshot de arranque para el microservicio de búsqueda: los hashes SHA-256 de todas las api
 * keys (nunca su valor), los metadatos de todas las listas y los modelos de embedding (búsqueda
 * los precalienta al arrancar).
 */
public record BootstrapResponse(List<ApiKeyEntry> apiKeys, List<ListEntry> lists, List<String> embeddingModels) {

    public record ApiKeyEntry(Long id, Long userId, String keyHash) {
    }

    public record ListEntry(Long id, Long userId, String name, boolean isPublic) {
    }
}
