package com.xeye.backend.training.application.command;

import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingTime;

import java.util.Map;

/**
 * Callback de progreso/finalización de un training, del webhook real o del provider mock.
 * {@code status} es un string crudo ("optimizing"/"training"/"completed"/"failed").
 * {@code generatedDescriptions} (id de elemento -> enriquecimiento LLM del worker) solo llega
 * al completar y se cachea en los elementos para que el siguiente training no repita ese trabajo.
 * {@code describedCount} = elementos con descripción LLM (caché + generadas) al calcular los
 * embeddings; puede ser menor que los elementos del run si el LLM falló en algunos.
 */
public record TrainingUpdateCommand(
        Long trainingId,
        String status,
        String embeddingsData,
        String model,
        TrainingTime time,
        TrainingCost cost,
        String error,
        Map<Long, String> generatedDescriptions,
        Integer describedCount) {
}
