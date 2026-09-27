package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.user.application.port.out.CaptchaVerifier;
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

/** Cloudflare Turnstile: {@code POST /turnstile/v0/siteverify}. Falla cerrado (sin respuesta válida no pasa). */
@Component
@ConditionalOnProperty(name = "xeye.auth.captcha.provider", havingValue = "turnstile")
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

    private final RestClient http;
    private final String secret;

    public TurnstileCaptchaVerifier(AuthProperties props) {
        if (props.captcha().secret().isBlank()) {
            throw new IllegalStateException("CAPTCHA_PROVIDER=turnstile requires CAPTCHA_SECRET");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .baseUrl("https://challenges.cloudflare.com")
                .requestFactory(requestFactory)
                .build();
        this.secret = props.captcha().secret();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("secret", secret);
            body.put("response", token);
            if (remoteIp != null) {
                body.put("remoteip", remoteIp);
            }
            Map<String, Object> result = http.post()
                    .uri("/turnstile/v0/siteverify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RuntimeException e) {
            log.warn("Turnstile verification failed: {}", e.getMessage());
            return false;
        }
    }
}
