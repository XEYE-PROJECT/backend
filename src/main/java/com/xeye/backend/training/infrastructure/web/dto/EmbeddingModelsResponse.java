package com.xeye.backend.training.infrastructure.web.dto;

import java.util.List;

/** Modelos de embedding con los que se puede lanzar un training; {@code defaultModel} es el primero. */
public record EmbeddingModelsResponse(List<String> models, String defaultModel) {

    public static EmbeddingModelsResponse of(List<String> models) {
        return new EmbeddingModelsResponse(models, models.isEmpty() ? null : models.get(0));
    }
}
