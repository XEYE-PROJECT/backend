package com.xeye.backend.training.application.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.training.domain.model.TrainingOption;

import java.util.List;

/**
 * Todo lo que el worker de training necesita para ejecutar un job. Este record ES el formato
 * de red: docker lo serializa tal cual y RunPod copia las mismas claves en su objeto
 * {@code input}. De ahí los nombres en snake_case — el worker (Python) los lee literalmente,
 * así que renombrar un componente rompe en silencio todos los providers.
 * <p>
 * El secreto del webhook NO viaja aquí a propósito: el job se escribe en disco (docker) o lo
 * almacena RunPod. Viaja {@code webhookToken}, un token derivado de él con HMAC que solo vale
 * para reportar sobre <em>este</em> entrenamiento ({@code shared/security/WebhookTokens}).
 */
public record TrainingLaunchCommand(
        @JsonProperty("training_id") Long trainingId,
        @JsonProperty("list_id") Long listId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("webhook_token") String webhookToken,
        ListPayload list,
        List<ElementPayload> elements,
        List<TrainingOption> options) {

    /**
     * {@code llmEnrichment} false = la lista ha renunciado al LLM: el worker no envía ningún
     * texto a un modelo (ni local ni remoto) aunque las opciones del run digan otra cosa.
     */
    public record ListPayload(Long id, String name, String description,
                              @JsonProperty("llm_enrichment") boolean llmEnrichment) {
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
