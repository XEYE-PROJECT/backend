package com.xeye.backend.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Última barrera antes de servir tráfico en producción: comprueba que ningún secreto es un
 * valor de desarrollo o demasiado corto y que los proveedores y URLs son los de producción.
 * Si algo falla, lanza y el arranque aborta con un mensaje que nombra la variable de entorno.
 * <p>
 * Lee las claves por {@link Environment} en vez de inyectar los records de cada módulo para
 * que {@code shared} no dependa de {@code training} (las dependencias entre módulos son
 * acíclicas). Solo existe con el perfil {@code prod}.
 */
@Component
@Profile("prod")
public class ProductionConfigGuard {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigGuard.class);

    static final int MIN_SECRET_LENGTH = 32;

    /** Valores que aparecen en application-dev.yml, .env.example o compose de desarrollo. */
    static final Set<String> KNOWN_INSECURE_VALUES = Set.of(
            "dev-only-insecure-secret-change-me-please-0123456789",
            "dev-webhook-secret",
            "dev-internal-token",
            "changeme", "change-me", "secret", "password",
            "xeye", "rootpassword");

    private final Environment environment;

    public ProductionConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        List<String> problems = check(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration:\n - "
                    + String.join("\n - ", problems));
        }
        log.info("Production configuration verified: secrets, providers and origins look sane");
    }

    /** Devuelve una descripción por cada problema encontrado (vacía = configuración válida). */
    static List<String> check(Environment env) {
        List<String> problems = new ArrayList<>();

        String jwtSecret = env.getProperty("xeye.jwt.secret", "");
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            problems.add("JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " bytes");
        }
        if (isInsecure(jwtSecret)) {
            problems.add("JWT_SECRET is a known development value");
        }

        checkSharedSecret(problems, env.getProperty("xeye.training.webhook-secret", ""), "TRAINING_WEBHOOK_SECRET");
        checkSharedSecret(problems, env.getProperty("xeye.search.internal-token", ""), "SEARCH_INTERNAL_TOKEN");

        String dbPassword = env.getProperty("spring.datasource.password", "");
        if (dbPassword.isBlank()) {
            problems.add("DB_PASSWORD must not be blank");
        } else if (isInsecure(dbPassword)) {
            problems.add("DB_PASSWORD is a known development value");
        }

        String trainingProvider = env.getProperty("xeye.training.provider", "");
        if ("mock".equalsIgnoreCase(trainingProvider)) {
            problems.add("TRAINING_PROVIDER=mock is not allowed in production (use docker or runpod)");
        }
        String searchProvider = env.getProperty("xeye.search.provider", "");
        if (!"http".equalsIgnoreCase(searchProvider)) {
            problems.add("SEARCH_PROVIDER must be 'http' in production (was '" + searchProvider + "')");
        }

        String origins = env.getProperty("xeye.cors.allowed-origins", "");
        for (String origin : origins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("https://")) {
                problems.add("CORS_ORIGINS must contain only https:// origins (found '" + trimmed + "')");
            }
        }

        String callbackBase = env.getProperty("xeye.training.callback-base-url", "");
        if (!callbackBase.startsWith("https://")) {
            problems.add("BACKEND_URL must be the public https:// URL of this backend (found '" + callbackBase + "')");
        }

        String frontendUrl = env.getProperty("xeye.auth.frontend-url", "");
        if (!frontendUrl.startsWith("https://")) {
            problems.add("FRONTEND_URL must be the public https:// URL of the console (found '" + frontendUrl + "')");
        }
        String emailProvider = env.getProperty("xeye.email.provider", "");
        boolean verification = env.getProperty("xeye.auth.require-email-verification", Boolean.class, true);
        if ("log".equalsIgnoreCase(emailProvider) && verification) {
            problems.add("EMAIL_PROVIDER=log cannot deliver verification emails in production (use smtp or resend, or set AUTH_REQUIRE_EMAIL_VERIFICATION=false)");
        }
        if ("smtp".equalsIgnoreCase(emailProvider)) {
            if (env.getProperty("spring.mail.username", "").isBlank() || env.getProperty("spring.mail.password", "").isBlank()) {
                problems.add("SMTP_USERNAME and SMTP_PASSWORD must be set when EMAIL_PROVIDER=smtp");
            }
        }
        if ("resend".equalsIgnoreCase(emailProvider) && env.getProperty("xeye.email.resend-api-key", "").isBlank()) {
            problems.add("RESEND_API_KEY must be set when EMAIL_PROVIDER=resend");
        }
        return problems;
    }

    private static void checkSharedSecret(List<String> problems, String value, String variable) {
        if (value.isBlank()) {
            problems.add(variable + " must not be blank");
            return;
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            problems.add(variable + " must be at least " + MIN_SECRET_LENGTH + " characters (openssl rand -hex 32)");
        }
        if (isInsecure(value)) {
            problems.add(variable + " is a known development value");
        }
    }

    private static boolean isInsecure(String value) {
        return KNOWN_INSECURE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
