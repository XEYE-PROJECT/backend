package com.xeye.backend.training.infrastructure.web;

import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.training.application.port.in.TrainingCompletionHandler;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.infrastructure.web.dto.TrainingWebhookRequest;
import com.xeye.backend.training.infrastructure.web.dto.WebhookAck;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback de progreso/finalización del worker de training. Ruta pública (ver SecurityConfig)
 * protegida por el secreto compartido {@code X-Webhook-Token}.
 */
@RestController
@RequestMapping("/webhooks")
public class TrainingWebhookController {

    private final TrainingCompletionHandler completionHandler;
    private final String webhookSecret;

    public TrainingWebhookController(TrainingCompletionHandler completionHandler, TrainingProperties properties) {
        this.completionHandler = completionHandler;
        this.webhookSecret = properties.webhookSecret();
    }

    @PostMapping("/training-update")
    public WebhookAck update(@RequestHeader(value = "X-Webhook-Token", required = false) String token,
                             @RequestBody TrainingWebhookRequest request) {
        if (webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.equals(token)) {
            throw new ForbiddenException("Invalid webhook token");
        }
        completionHandler.applyUpdate(request.toCommand());
        return new WebhookAck(true, "Webhook processed");
    }
}
