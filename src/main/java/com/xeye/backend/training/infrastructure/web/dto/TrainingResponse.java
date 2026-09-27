package com.xeye.backend.training.infrastructure.web.dto;

import com.xeye.backend.training.application.port.in.TrainingUseCases.ListedTraining;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingTime;

import java.time.Instant;
import java.util.List;

/** Vista de un training. Los embeddings nunca se devuelven (solo el flag {@code hasEmbeddings}). */
public record TrainingResponse(
        Long id,
        Long listId,
        Long userId,
        String instanceId,
        String status,
        List<TrainingOption> options,
        Integer elementCount,
        /** Elementos con descripción LLM al calcular los embeddings (null en trainings antiguos). */
        Integer describedCount,
        String model,
        TrainingTime time,
        TrainingCost cost,
        String error,
        boolean inUse,
        boolean hasEmbeddings,
        Boolean usable,
        /** Posición en la cola (1 = el siguiente) mientras el estado es {@code queued}; null en otro caso. */
        Integer queuePosition,
        /** Último callback del worker (latido); null hasta que se lanza. */
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TrainingResponse from(Training training) {
        return from(training, null, null);
    }

    public static TrainingResponse from(ListedTraining listed) {
        return from(listed.training(), listed.usable(), listed.queuePosition());
    }

    public static TrainingResponse from(Training training, Boolean usable, Integer queuePosition) {
        return new TrainingResponse(
                training.id(),
                training.listId(),
                training.userId(),
                training.instanceId(),
                training.status().value(),
                training.options(),
                training.elementIds() == null ? null : training.elementIds().size(),
                training.describedCount(),
                training.model(),
                training.time(),
                training.cost(),
                training.error(),
                training.inUse(),
                training.hasEmbeddings(),
                usable,
                queuePosition,
                training.lastHeartbeatAt(),
                training.createdAt(),
                training.updatedAt());
    }
}
