package com.xeye.backend.training.domain.model;

import java.util.Locale;

/** Ciclo de vida de un training. Se persiste como su valor en minúsculas. */
public enum TrainingStatus {

    /** Creado por una edición de lista/elemento; espera a que el usuario lo lance (uno por lista). */
    PENDING,
    QUEUED,
    INITIALIZED,
    OPTIMIZING,
    TRAINING,
    COMPLETED,
    FAILED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Run lanzado que no ha llegado a un estado terminal (candidato al barrido de estancados). */
    public boolean isRunning() {
        return this == QUEUED || this == INITIALIZED || this == OPTIMIZING || this == TRAINING;
    }

    public static TrainingStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Training status must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TrainingStatus status : values()) {
            if (status.value().equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown training status: " + value);
    }
}
