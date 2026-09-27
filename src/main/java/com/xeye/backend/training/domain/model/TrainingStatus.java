package com.xeye.backend.training.domain.model;

import java.util.Locale;

/** Ciclo de vida de un training. Se persiste como su valor en minúsculas. */
public enum TrainingStatus {

    /** Creado por una edición de lista/elemento; espera a que el usuario lo lance (uno por lista). */
    PENDING,
    /** El usuario lo lanzó: espera en la cola a que el despachador tenga hueco. */
    QUEUED,
    /** Entregado al provider (contenedor arrancado / job enviado); aún sin callbacks. */
    INITIALIZED,
    OPTIMIZING,
    TRAINING,
    COMPLETED,
    FAILED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Entregado a un worker y sin terminar: ocupa un hueco del cupo y lo vigila el barrido de estancados. */
    public boolean isLaunched() {
        return this == INITIALIZED || this == OPTIMIZING || this == TRAINING;
    }

    /** En cola o lanzado: la lista no admite otro run mientras tanto. */
    public boolean isInProgress() {
        return this == QUEUED || isLaunched();
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * Máquina de estados de los callbacks del worker: solo se avanza. Un estado repetido es un
     * latido (permitido, no cambia nada); ir hacia atrás (training → optimizing) o tocar un run
     * terminado se ignora. Un run estancado (FAILED por el barrido) sí puede completar tarde.
     */
    public boolean canTransitionTo(TrainingStatus next) {
        return switch (this) {
            case PENDING -> next == QUEUED;
            case QUEUED -> next == INITIALIZED || next == OPTIMIZING || next == TRAINING
                    || next == COMPLETED || next == FAILED;
            case INITIALIZED -> next == OPTIMIZING || next == TRAINING || next == COMPLETED || next == FAILED;
            case OPTIMIZING -> next == OPTIMIZING || next == TRAINING || next == COMPLETED || next == FAILED;
            case TRAINING -> next == TRAINING || next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
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
