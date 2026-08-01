package com.xeye.backend.training.application.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.training.domain.model.TrainingOption;

import java.util.List;

/**
 * Todo lo que el worker de training necesita para ejecutar un job. Este record ES el formato
 * de red: docker lo serializa tal cual y RunPod copia las mismas claves en su objeto
 * {@code input}. De ahí los nombres en snake_case — el worker (Python) los lee literalmente,
 * así que renombrar un componente rompe en silencio todos los providers.
 */
public record TrainingLaunchCommand(
        @JsonProperty("training_id") Long trainingId,
        @JsonProperty("list_id") Long listId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("webhook_secret") String webhookSecret,
        ListPayload list,
        List<ElementPayload> elements,
        List<TrainingOption> options) {

    public record ListPayload(Long id, String name, String description) {
    }

    /**
     * {@code generatedDescription} es el enriquecimiento LLM previo del propio worker (null si
     * cambió el texto/descripción del elemento); devolvérselo le permite saltarse el LLM
     * en todo lo que no cambió.
     */
    public record ElementPayload(Long id, String text, String description,
                                 @JsonProperty("generated_description") String generatedDescription,
                                 boolean trained) {
    }
}
