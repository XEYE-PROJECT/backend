package com.xeye.backend.training.infrastructure.web.dto;

/**
 * Lanzamiento de un training pendiente. {@code embeddingModel} null/vacío = el por defecto
 * configurado; {@code regenerateDescriptions} true = regenerar el enriquecimiento LLM de todos
 * los elementos en vez de reutilizar el cacheado (null = false).
 */
public record LaunchTrainingRequest(String embeddingModel, Boolean regenerateDescriptions) {

    public boolean regenerate() {
        return Boolean.TRUE.equals(regenerateDescriptions);
    }
}
