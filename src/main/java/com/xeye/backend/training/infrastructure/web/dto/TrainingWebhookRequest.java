package com.xeye.backend.training.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Body de {@code POST /webhooks/training-update}. Los nombres de campo son exactamente los
 * que envía el training-service de XEYE (one-shot y handler de RunPod). {@code embeddings_data}
 * no tiene tope aquí: lo acota el límite de body del webhook ({@code HTTP_WEBHOOK_MAX_BODY_BYTES}).
 */
public record TrainingWebhookRequest(
        @NotNull @JsonProperty("training_id") Long trainingId,
        @JsonProperty("list_id") Long listId,
        @NotBlank @Size(max = 30) String status,
        @JsonProperty("embeddings_data") String embeddingsData,
        @Size(max = 4000) String model,
        @Size(max = 2000) String error,
        TimePayload time,
        CostPayload cost,
        /** Id de elemento (clave JSON, de ahí String) -> enriquecimiento LLM del worker. */
        @Size(max = 10000) @JsonProperty("generated_descriptions") Map<String, @Size(max = 16000) String> generatedDescriptions,
        /** Elementos con descripción LLM (caché + generadas) al calcular los embeddings. */
        @JsonProperty("described_count") Integer describedCount) {

    public record TimePayload(
            @JsonProperty("optimizing_seconds") Long optimizingSeconds,
            @JsonProperty("training_seconds") Long trainingSeconds,
            @JsonProperty("total_seconds") Long totalSeconds) {
    }

    /** El worker solo reporta cómputo; el precio (fijo + descripciones) ya lo fijó el backend al lanzar. */
    public record CostPayload(Double runpod, Double total) {
    }

    public TrainingUpdateCommand toCommand() {
        TrainingTime trainingTime = time == null ? null
                : new TrainingTime(time.optimizingSeconds(), time.trainingSeconds(), time.totalSeconds());
        TrainingCost trainingCost = cost == null ? null
                : new TrainingCost(cost.runpod(), null, null, cost.total());
        return new TrainingUpdateCommand(trainingId, listId, status, embeddingsData, model, trainingTime,
                trainingCost, error, parseGeneratedDescriptions(), describedCount);
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
