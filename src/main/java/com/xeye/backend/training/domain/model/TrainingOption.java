package com.xeye.backend.training.domain.model;

/** Una opción de training, p. ej. {@code {"key":"train_all","value":true}}. */
public record TrainingOption(String key, Object value) {
}
