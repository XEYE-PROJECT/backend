package com.xeye.backend.training.domain.model;

/** Desglose de tiempos por fase reportado por el worker de training (segundos; puede ser null). */
public record TrainingTime(Long optimizingSeconds, Long trainingSeconds, Long totalSeconds) {
}
