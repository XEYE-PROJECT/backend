package com.xeye.backend.training.infrastructure.web.dto;

/**
 * Lanzamiento de un training pendiente. {@code embeddingModel} null/vacío = el por defecto
 * configurado; {@code regenerateDescriptions} true = regenerar el enriquecimiento LLM de todos
 * los elementos en vez de reutilizar el cacheado (null = false); {@code noDescriptions} true =
 * entrenar sin descripciones IA (gana a regenerar).
 */
public record LaunchTrainingRequest(String embeddingModel, Boolean regenerateDescriptions,
                                    Boolean noDescriptions) {

    public boolean regenerate() {
        return Boolean.TRUE.equals(regenerateDescriptions);
    }

    public boolean skipDescriptions() {
        return Boolean.TRUE.equals(noDescriptions);
    }
}
