package com.xeye.backend.shared.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sentry solo se activa con {@code SENTRY_DSN} (ver application.yml). Este callback se aplica a
 * todo evento y garantiza que ninguna credencial de cabecera (JWT, secreto del webhook, token
 * interno) sale hacia Sentry aunque el SDK capture la petición.
 */
@Configuration
public class SentryConfig {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "x-webhook-token", "x-internal-token", "x-api-key");

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> {
            Request request = event.getRequest();
            if (request != null && request.getHeaders() != null) {
                Map<String, String> headers = new HashMap<>(request.getHeaders());
                headers.keySet().removeIf(name -> SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)));
                request.setHeaders(headers);
            }
            return event;
        };
    }
}
