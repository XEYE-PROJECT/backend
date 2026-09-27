package com.xeye.backend.shared.email;

import com.xeye.backend.shared.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Proveedor {@code resend}: {@code POST https://api.resend.com/emails}. Los fallos se loguean y
 * no se propagan (el usuario puede pedir un reenvío); la API key nunca aparece en el log.
 */
@Component
@ConditionalOnProperty(name = "xeye.email.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient http;
    private final String from;
    private final String replyTo;

    public ResendEmailSender(EmailProperties properties) {
        if (properties.resendApiKey().isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=resend requires RESEND_API_KEY");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.resendApiKey())
                .build();
        this.from = properties.from();
        this.replyTo = properties.replyTo();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            Map<String, Object> body = new HashMap<>(Map.of("from", from, "to", new String[] {message.to()},
                    "subject", message.subject(), "text", message.text(), "html", message.html()));
            if (!replyTo.isBlank()) {
                body.put("reply_to", replyTo);
            }
            http.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email sent via Resend to {} ({})", message.to(), message.subject());
        } catch (RuntimeException e) {
            log.error("Failed to send email to {} ({}): {}", message.to(), message.subject(), e.getMessage());
        }
    }
}
