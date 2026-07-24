package com.xeye.backend.training.domain.model;

/** Coste de cómputo reportado por el worker de training (0 en el provider local/mock). */
public record TrainingCost(Double runpod, Double total) {
}
