package com.xeye.backend.contracts;

import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.infrastructure.web.dto.TrainingWebhookRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que el worker ENVÍA al backend (webhook), deserializado en el DTO real y validado con Bean Validation. */
class InboundContractsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void completedWebhookDeserializesAndValidates() {
        TrainingWebhookRequest request = MAPPER.readValue(
                ContractFixtures.read("training-webhook-completed.json"), TrainingWebhookRequest.class);

        assertTrue(VALIDATOR.validate(request).isEmpty());
        assertEquals(42L, request.trainingId());
        assertEquals(7L, request.listId());
        assertEquals("completed", request.status());
        assertEquals(2, request.describedCount());
        assertEquals(5L, request.time().totalSeconds());
        assertEquals(0.000694, request.cost().total());
        // base64(np.save) del worker: cabecera NumPy en los primeros bytes.
        byte[] embeddings = Base64.getDecoder().decode(request.embeddingsData());
        assertEquals("NUMPY", new String(embeddings, 1, 5, java.nio.charset.StandardCharsets.US_ASCII));

        TrainingUpdateCommand command = request.toCommand();
        assertEquals(Map.of(101L, ContractFixtures.tree("training-webhook-completed.json")
                .get("generated_descriptions").get("101").asString()), command.generatedDescriptions());
        assertTrue(command.model().contains("\"embedding_model\""));
    }

    @Test
    void phaseWebhookIsAHeartbeatWithoutPayload() {
        TrainingWebhookRequest request = MAPPER.readValue(
                ContractFixtures.read("training-webhook-phase.json"), TrainingWebhookRequest.class);

        assertTrue(VALIDATOR.validate(request).isEmpty());
        assertEquals("training", request.status());
        assertNull(request.embeddingsData());
        assertNull(request.error());
    }

    @Test
    void failedWebhookCarriesTheError() {
        TrainingWebhookRequest request = MAPPER.readValue(
                ContractFixtures.read("training-webhook-failed.json"), TrainingWebhookRequest.class);

        assertTrue(VALIDATOR.validate(request).isEmpty());
        assertEquals("failed", request.status());
        assertTrue(request.error().startsWith("CUDA out of memory"));
    }
}
