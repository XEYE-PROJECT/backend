package com.xeye.backend.training.infrastructure.launcher;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.shared.http.OutboundHttp;
import com.xeye.backend.training.config.TrainingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envía un job a un endpoint Serverless de RunPod ({@code POST /v2/{endpointId}/run}). Las
 * claves del objeto {@code input} coinciden exactamente con lo que lee el training-service de
 * XEYE, así el worker corre sin cambios. El propio worker entrega el resultado a nuestro
 * webhook con {@code X-Webhook-Token}: el token por entrenamiento que viaja en el job (derivado
 * del secreto con HMAC); el secreto en sí nunca sale del backend. La llamada tiene timeout
 * de conexión y de lectura ({@code RUNPOD_TIMEOUT_SECONDS}) y NO se reintenta: un {@code /run}
 * repetido lanzaría (y cobraría) dos jobs; si falla, el training queda fallido y la lista pendiente.
 */
@Component
@ConditionalOnProperty(name = "xeye.training.provider", havingValue = "runpod")
public class RunPodTrainingLauncher implements TrainingLauncher {

    private static final Logger log = LoggerFactory.getLogger(RunPodTrainingLauncher.class);

    private final TrainingProperties.RunPod config;
    private final RestClient http;

    public RunPodTrainingLauncher(TrainingProperties properties) {
        this.config = properties.runpod();
        int timeout = config == null || config.timeoutSeconds() <= 0 ? 30 : config.timeoutSeconds();
        this.http = OutboundHttp.client("https://api.runpod.ai/v2", Duration.ofSeconds(timeout));
    }

    @Override
    public String launch(TrainingLaunchCommand command) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("training_id", command.trainingId());
        input.put("list_id", command.listId());
        input.put("user_id", command.userId());
        input.put("callback_url", command.callbackUrl());
        input.put("webhook_token", command.webhookToken());
        input.put("list", command.list());
        input.put("elements", command.elements());
        input.put("options", command.options() == null ? List.of() : command.options());

        // Sin "webhook" nativo de RunPod: llegaría sin cabecera y solo generaría 403 de ruido.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);

        RunPodResponse response = http.post()
                .uri("/{endpointId}/run", config.endpointId())
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RunPodResponse.class);

        if (response == null || response.id() == null) {
            throw new IllegalStateException("RunPod did not return a job id");
        }
        log.info("Submitted RunPod job {} for training {}", response.id(), command.trainingId());
        return response.id();
    }

    private record RunPodResponse(String id, String status) {
    }
}
