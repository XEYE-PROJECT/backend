package com.xeye.backend.training.domain.model;

/**
 * Coste del entrenamiento. {@code fixed} es el precio fijo por entrenamiento (preestablecido
 * por el backend al lanzar), {@code enrichment} el precio de las descripciones LLM — estimado
 * al lanzar y ajustado al completar a las realmente generadas — y {@code runpod} el cómputo
 * por tiempo reportado por el worker si está tarificado (0 en el provider local/mock).
 */
public record TrainingCost(Double runpod, Double fixed, Double enrichment, Double total) {
}
