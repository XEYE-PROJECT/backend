package com.xeye.backend.training.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingTime;

import java.util.HashMap;
import java.util.Map;

/**
 * Body de {@code POST /webhooks/training-update}. Los nombres de campo son exactamente los
 * que envía el training-service de XEYE (one-shot y handler de RunPod).
 */
public record TrainingWebhookRequest(
        @JsonProperty("training_id") Long trainingId,
        @JsonProperty("list_id") Long listId,
        String status,
        @JsonProperty("embeddings_data") String embeddingsData,
        String model,
        String error,
        TimePayload time,
        CostPayload cost,
        /** Id de elemento (clave JSON, de ahí String) -> enriquecimiento LLM del worker. */
        @JsonProperty("generated_descriptions") Map<String, String> generatedDescriptions) {

    public record TimePayload(
            @JsonProperty("optimizing_seconds") Long optimizingSeconds,
            @JsonProperty("training_seconds") Long trainingSeconds,
            @JsonProperty("total_seconds") Long totalSeconds) {
    }

    public record CostPayload(Double runpod, Double total) {
    }

    public TrainingUpdateCommand toCommand() {
        TrainingTime trainingTime = time == null ? null
                : new TrainingTime(time.optimizingSeconds(), time.trainingSeconds(), time.totalSeconds());
        TrainingCost trainingCost = cost == null ? null
                : new TrainingCost(cost.runpod(), cost.total());
        return new TrainingUpdateCommand(trainingId, status, embeddingsData, model, trainingTime, trainingCost,
                error, parseGeneratedDescriptions());
    }

    /** Omite las entradas cuya clave no es numérica en vez de fallar el callback entero. */
    private Map<Long, String> parseGeneratedDescriptions() {
        if (generatedDescriptions == null || generatedDescriptions.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> parsed = new HashMap<>();
        generatedDescriptions.forEach((key, value) -> {
            try {
                parsed.put(Long.valueOf(key.trim()), value);
            } catch (NumberFormatException ignored) {
                // no es un id de elemento; se descarta
            }
        });
        return parsed;
    }
}
