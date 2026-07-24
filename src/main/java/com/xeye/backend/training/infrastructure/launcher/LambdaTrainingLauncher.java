package com.xeye.backend.training.infrastructure.launcher;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.training.config.TrainingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Provider AWS Lambda: invoca la función con el job como evento, en <b>asíncrono</b>
 * ({@code InvocationType=Event}) — Lambda encola, devuelve 202 y el worker responde por nuestro
 * webhook, el mismo contrato que el resto de providers. Una invocación síncrona ataría un hilo
 * al training entero y moriría en el muro de 900 s de Lambda.
 * Las credenciales salen de la cadena AWS por defecto (env, perfil o rol de instancia).
 */
@Component
@ConditionalOnProperty(name = "xeye.training.provider", havingValue = "lambda")
public class LambdaTrainingLauncher implements TrainingLauncher {

    private static final Logger log = LoggerFactory.getLogger(LambdaTrainingLauncher.class);

    private final TrainingProperties.Lambda config;
    private final ObjectMapper json;
    private final LambdaClient lambda;

    public LambdaTrainingLauncher(TrainingProperties properties, ObjectMapper json) {
        this.config = properties.lambda();
        this.json = json;
        if (config.functionName() == null || config.functionName().isBlank()) {
            throw new IllegalStateException("xeye.training.provider=lambda requires TRAINING_LAMBDA_FUNCTION");
        }
        LambdaClientBuilder builder = LambdaClient.builder()
                .httpClient(UrlConnectionHttpClient.create());
        if (config.region() != null && !config.region().isBlank()) {
            builder.region(Region.of(config.region()));
        }
        this.lambda = builder.build();
    }

    @Override
    public String launch(TrainingLaunchCommand command) {
        SdkBytes payload = SdkBytes.fromString(json.writeValueAsString(command), StandardCharsets.UTF_8);
        InvokeResponse response = lambda.invoke(InvokeRequest.builder()
                .functionName(config.functionName())
                .invocationType(InvocationType.EVENT)
                .payload(payload)
                .build());

        if (response.statusCode() == null || response.statusCode() != 202) {
            throw new IllegalStateException("Lambda did not accept the job (status "
                    + response.statusCode() + ", error " + response.functionError() + ")");
        }
        String requestId = response.responseMetadata().requestId();
        log.info("Queued Lambda invocation {} for training {}", requestId, command.trainingId());
        return requestId;
    }
}
