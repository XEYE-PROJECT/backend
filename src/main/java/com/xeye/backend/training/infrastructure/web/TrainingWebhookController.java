package com.xeye.backend.training.infrastructure.web;

import com.xeye.backend.training.application.port.in.TrainingCompletionHandler;
import com.xeye.backend.training.infrastructure.web.dto.TrainingWebhookRequest;
import com.xeye.backend.training.infrastructure.web.dto.WebhookAck;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback de progreso/finalización del worker de training. La autenticación no vive aquí:
 * {@code SharedSecretAuthenticationFilter} (ver {@code SecurityConfig}) exige la cabecera
 * {@code X-Webhook-Token} en {@code /webhooks/**} y rechaza con 403 antes de llegar al controlador.
 */
@RestController
@RequestMapping("/webhooks")
public class TrainingWebhookController {

    private final TrainingCompletionHandler completionHandler;

    public TrainingWebhookController(TrainingCompletionHandler completionHandler) {
        this.completionHandler = completionHandler;
    }

    @PostMapping("/training-update")
    public WebhookAck update(@Valid @RequestBody TrainingWebhookRequest request) {
        completionHandler.applyUpdate(request.toCommand());
        return new WebhookAck(true, "Webhook processed");
    }
}
