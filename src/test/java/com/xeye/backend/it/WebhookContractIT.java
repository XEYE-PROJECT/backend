package com.xeye.backend.it;

import com.xeye.backend.contracts.ContractFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contrato worker → backend con el mapper y la validación reales: cada fixture del webhook
 * deserializa y pasa {@code @Valid}. El training 42 no existe en esta BD, así que la respuesta
 * correcta es 404 {@code NOT_FOUND}; un 400 significaría que el worker y el backend ya no hablan
 * el mismo JSON.
 */
class WebhookContractIT extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"training-webhook-phase.json", "training-webhook-completed.json",
        "training-webhook-failed.json"})
    void workerPayloadsAreAcceptedByTheWebhook(String fixture) {
        Response response = exchange("POST", "/webhooks/training-update", ContractFixtures.read(fixture), null,
                Map.of("X-Webhook-Token", WEBHOOK_SECRET));
        assertEquals(404, response.status(), () -> fixture + " -> " + response.body());
        assertEquals("NOT_FOUND", response.code());
    }
}
