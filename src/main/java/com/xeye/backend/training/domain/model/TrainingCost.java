package com.xeye.backend.training.domain.model;

/**
 * Coste del entrenamiento. Precio al usuario: {@code fixed} (fijo por entrenamiento,
 * preestablecido por el backend al lanzar) + {@code enrichment} (descripciones LLM, estimado al
 * lanzar y ajustado al completar a las realmente generadas) = {@code total} (más el cómputo
 * {@code runpod} si está tarificado). Coste real reportado por el worker, solo informativo:
 * {@code runpod} (tiempo x precio/hora de la máquina) y {@code llm} (tokens x tarifa del
 * proveedor), con los tokens que lo explican. Null en runs anteriores a cada campo.
 */
public record TrainingCost(Double runpod, Double llm, Double fixed, Double enrichment, Double total,
                           Long llmInputTokens, Long llmOutputTokens) {

    public TrainingCost(Double runpod, Double fixed, Double enrichment, Double total) {
        this(runpod, null, fixed, enrichment, total, null, null);
    }
}
