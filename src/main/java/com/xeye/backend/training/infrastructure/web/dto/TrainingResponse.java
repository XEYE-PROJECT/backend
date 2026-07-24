package com.xeye.backend.training.infrastructure.web.dto;

import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingTime;

import java.time.Instant;
import java.util.List;

/** Vista de un training. El blob grande {@code embeddings_data} no se devuelve (solo un flag). */
public record TrainingResponse(
        Long id,
        Long listId,
        Long userId,
        String instanceId,
        String status,
        List<TrainingOption> options,
        /** Cuántos elementos embebió el run (null hasta el lanzamiento): compararlo con el
         *  tamaño actual de la lista delata un training que ya no cuadra con ella. */
        Integer elementCount,
        String model,
        TrainingTime time,
        TrainingCost cost,
        String error,
        boolean inUse,
        boolean hasEmbeddings,
        Instant createdAt,
        Instant updatedAt) {

    public static TrainingResponse from(Training training) {
        return new TrainingResponse(
                training.id(),
                training.listId(),
                training.userId(),
                training.instanceId(),
                training.status().value(),
                training.options(),
                training.elementIds() == null ? null : training.elementIds().size(),
                training.model(),
                training.time(),
                training.cost(),
                training.error(),
                training.inUse(),
                training.embeddingsData() != null,
                training.createdAt(),
                training.updatedAt());
    }
}
