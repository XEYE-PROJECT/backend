package com.xeye.backend.training.infrastructure.web.dto;

/** Lanzamiento de un training pendiente. {@code embeddingModel} null/vacío = el por defecto configurado. */
public record LaunchTrainingRequest(String embeddingModel) {
}
