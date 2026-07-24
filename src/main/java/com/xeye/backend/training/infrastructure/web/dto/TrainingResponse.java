package com.xeye.backend.training.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingTime;

/** Vista de un training. El blob grande {@code embeddings_data} no se devuelve (solo un flag). */
public record TrainingResponse(
        Long id,
        Long listId,
        Long userId,
        String instanceId,
        String status,
        List<TrainingOption> options,
        Integer elementCount,
        String model,
        TrainingTime time,
        TrainingCost cost,
        String error,
        boolean inUse,
        boolean hasEmbeddings,
        Boolean usable,
        Instant createdAt,
        Instant updatedAt) {

    public static TrainingResponse from(Training training) {
        return from(training, null);
    }

    public static TrainingResponse from(Training training, Boolean usable) {
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
                usable,
                training.createdAt(),
                training.updatedAt());
    }
}
