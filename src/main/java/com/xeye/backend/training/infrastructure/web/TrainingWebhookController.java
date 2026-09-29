package com.xeye.backend.training.infrastructure.web;

import com.xeye.backend.training.application.TrainingDispatcher;
import com.xeye.backend.training.application.command.TrainingUpdateCommand;
import com.xeye.backend.training.application.port.in.TrainingCompletionHandler;
import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.shared.security.ServicePrincipal;
import com.xeye.backend.training.application.port.out.TrainingMetrics;
import com.xeye.backend.training.infrastructure.web.dto.TrainingWebhookRequest;
import com.xeye.backend.training.infrastructure.web.dto.WebhookAck;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Callback de progreso/finalización del worker de training. La autenticación no vive aquí:
 * {@code SharedSecretAuthenticationFilter} (ver {@code SecurityConfig}) exige en {@code /webhooks/**}
 * la cabecera {@code X-Webhook-Token} con el token por entrenamiento y rechaza con 403 antes de
 * llegar al controlador; aquí solo se comprueba que el training que firma el token es el del
 * cuerpo (un token de otro run no puede completar este). Tras cualquier callback se libera el
 * fichero del job del provider docker: el worker ya lo leyó.
 * Idempotente: un callback repetido o fuera de orden responde 200 con {@code applied: false}.
 * Dos callbacks simultáneos del mismo run chocan por bloqueo optimista; se reintenta un par de
 * veces (fuera de la transacción) y el segundo ve el estado ya aplicado.
 */
@RestController
@RequestMapping("/webhooks")
public class TrainingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TrainingWebhookController.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TrainingCompletionHandler completionHandler;
    private final TrainingDispatcher dispatcher;
    private final TrainingMetrics metrics;

    public TrainingWebhookController(TrainingCompletionHandler completionHandler, TrainingDispatcher dispatcher,
                                     TrainingMetrics metrics) {
        this.completionHandler = completionHandler;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    @PostMapping("/training-update")
    public WebhookAck update(@AuthenticationPrincipal ServicePrincipal worker,
                             @Valid @RequestBody TrainingWebhookRequest request) {
        if (worker == null || worker.trainingId() == null || !worker.trainingId().equals(request.trainingId())) {
            throw new ForbiddenException("The webhook token does not belong to training " + request.trainingId(),
                    "WEBHOOK_TOKEN_MISMATCH");
        }
        TrainingUpdateCommand command = request.toCommand();
        boolean applied = applyWithRetry(command);
        dispatcher.releaseJobInput(command.trainingId());
        String status = command.status().trim().toLowerCase(Locale.ROOT);
        if (applied && ("completed".equals(status) || "failed".equals(status))) {
            metrics.recordOutcome("completed".equals(status)
                    ? TrainingMetrics.OUTCOME_COMPLETED : TrainingMetrics.OUTCOME_FAILED);
            // Un run terminó: hay hueco en la cola.
            dispatcher.dispatch();
        }
        return new WebhookAck(true, applied ? "Webhook processed" : "Webhook ignored (already applied or out of order)",
                applied);
    }

    private boolean applyWithRetry(TrainingUpdateCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                return completionHandler.applyUpdate(command);
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.debug("Concurrent update on training {} (attempt {}); retrying", command.trainingId(), attempt);
            }
        }
    }
}
