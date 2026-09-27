package com.xeye.backend.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigGuardTest {

    private static final String STRONG = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private static MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("xeye.jwt.secret", STRONG)
                .withProperty("xeye.training.webhook-secret", STRONG)
                .withProperty("xeye.search.internal-token", STRONG)
                .withProperty("spring.datasource.password", "f00ba7-strong-db-password")
                .withProperty("xeye.training.provider", "runpod")
                .withProperty("xeye.search.provider", "http")
                .withProperty("xeye.cors.allowed-origins", "https://xeye.es,https://www.xeye.es")
                .withProperty("xeye.training.callback-base-url", "https://backend.xeye.es")
                .withProperty("xeye.auth.frontend-url", "https://xeye.es")
                .withProperty("xeye.email.provider", "resend")
                .withProperty("xeye.email.resend-api-key", "re_test_key")
                .withProperty("xeye.search.url", "http://search-service:8002");
    }

    @Test
    void emailAndFrontendConfigurationIsChecked() {
        List<String> problems = ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.auth.frontend-url", "http://localhost:3000")
                .withProperty("xeye.email.provider", "log"));
        assertEquals(2, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("FRONTEND_URL must be the public https://")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("EMAIL_PROVIDER=log")));

        // Sin verificación obligatoria, el provider log se tolera; resend sin clave no.
        assertEquals(List.of(), ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.email.provider", "log")
                .withProperty("xeye.auth.require-email-verification", "false")));
        assertEquals(1, ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.email.resend-api-key", "")).size());
        // smtp exige usuario y contraseña del buzón.
        assertEquals(1, ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.email.provider", "smtp")).size());
        assertEquals(List.of(), ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.email.provider", "smtp")
                .withProperty("spring.mail.username", "noreply@xeye.es")
                .withProperty("spring.mail.password", "s3cret")));
    }

    @Test
    void aCompleteProductionEnvironmentPasses() {
        assertEquals(List.of(), ProductionConfigGuard.check(validEnvironment()));
        new ProductionConfigGuard(validEnvironment()).verify();
    }

    @Test
    void developmentDefaultsAreRejectedByName() {
        MockEnvironment env = validEnvironment()
                .withProperty("xeye.jwt.secret", "dev-only-insecure-secret-change-me-please-0123456789")
                .withProperty("xeye.training.webhook-secret", "dev-webhook-secret")
                .withProperty("xeye.search.internal-token", "dev-internal-token")
                .withProperty("spring.datasource.password", "xeye");

        List<String> problems = ProductionConfigGuard.check(env);

        assertTrue(problems.stream().anyMatch(p -> p.startsWith("JWT_SECRET is a known")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("TRAINING_WEBHOOK_SECRET")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("SEARCH_INTERNAL_TOKEN")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("DB_PASSWORD")));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigGuard(env).verify());
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void shortOrBlankSecretsAreRejected() {
        MockEnvironment env = validEnvironment()
                .withProperty("xeye.jwt.secret", "short")
                .withProperty("xeye.training.webhook-secret", "")
                .withProperty("xeye.search.internal-token", "only-twenty-chars-xx");

        List<String> problems = ProductionConfigGuard.check(env);

        assertTrue(problems.stream().anyMatch(p -> p.contains("JWT_SECRET must be at least")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("TRAINING_WEBHOOK_SECRET must not be blank")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("SEARCH_INTERNAL_TOKEN must be at least")));
    }

    @Test
    void developmentProvidersAndInsecureUrlsAreRejected() {
        MockEnvironment env = validEnvironment()
                .withProperty("xeye.training.provider", "mock")
                .withProperty("xeye.search.provider", "log")
                .withProperty("xeye.cors.allowed-origins", "https://xeye.es,http://localhost:3000")
                .withProperty("xeye.training.callback-base-url", "http://localhost:8000");

        List<String> problems = ProductionConfigGuard.check(env);

        assertEquals(4, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("TRAINING_PROVIDER=mock")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("SEARCH_PROVIDER must be 'http'")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("http://localhost:3000")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("BACKEND_URL must be the public https://")));
    }

    @Test
    void localhostIsRejectedEvenOverHttps() {
        List<String> problems = ProductionConfigGuard.check(validEnvironment()
                .withProperty("xeye.cors.allowed-origins", "https://xeye.es,https://localhost:3000")
                .withProperty("xeye.auth.frontend-url", "https://127.0.0.1")
                .withProperty("xeye.training.callback-base-url", "https://user@localhost:8443/x")
                .withProperty("xeye.search.url", "http://localhost:8002"));
        assertEquals(4, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("CORS_ORIGINS must not contain localhost")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("FRONTEND_URL")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("BACKEND_URL")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("SEARCH_SERVICE_URL")));

        assertTrue(ProductionConfigGuard.isLocalHost("http://[::1]:8002"));
        assertTrue(ProductionConfigGuard.isLocalHost("https://app.localhost"));
        assertTrue(ProductionConfigGuard.isLocalHost("0.0.0.0:8000"));
        assertTrue(!ProductionConfigGuard.isLocalHost("http://search-service:8002"));
        assertTrue(!ProductionConfigGuard.isLocalHost("https://xeye.es/localhost"));
        assertTrue(!ProductionConfigGuard.isLocalHost("https://localhost.xeye.es"));
    }
}
