package com.xeye.backend.it;

import com.xeye.backend.shared.security.WebhookTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La cadena de seguridad completa ({@code SecurityConfig}) con HTTP real: JWT de usuario y de
 * admin, respuestas 401/403 en JSON, y los secretos compartidos del webhook y de la API interna.
 */
class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsPublic() {
        Response response = get("/actuator/health", null);
        assertEquals(200, response.status());
        assertEquals("UP", response.body().get("status").asString());
    }

    @Test
    void protectedEndpointWithoutTokenIs401Json() {
        Response response = get("/lists", null);
        assertEquals(401, response.status());
        assertEquals("UNAUTHENTICATED", response.code());
    }

    @Test
    void tamperedJwtIs401() {
        String token = loginAsAdmin();
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        Response response = get("/users/me", tampered);
        assertEquals(401, response.status());
        assertEquals("UNAUTHENTICATED", response.code());
    }

    @Test
    void loginIssuesAJwtThatAuthenticates() {
        String token = loginAsAdmin();
        Response me = get("/users/me", token);
        assertEquals(200, me.status());
        assertEquals(ADMIN_EMAIL, me.body().get("email").asString());
        assertNotNull(me.headers().getFirst("X-Request-Id"), "request id echoed");
    }

    @Test
    void wrongPasswordIs401WithoutRevealingWhy() {
        Response response = post("/auth/login", Map.of("email", ADMIN_EMAIL, "password", "not-the-password"), null);
        assertEquals(401, response.status());
        assertNotNull(response.code());
    }

    @Test
    void adminRoutesRequireTheAdminRole() {
        // Un usuario normal: registro (202, nunca inicia sesión solo) + verificación directa en BD.
        String email = "user-" + System.nanoTime() + "@it.local";
        Response registered = post("/auth/register", Map.of("name", "Ana", "surname", "IT", "email", email,
                "password", "correct horse battery staple"), null);
        assertEquals(202, registered.status());
        assertEquals(1, jdbc.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email));

        String userToken = login(email, "correct horse battery staple");
        Response forbidden = get("/admin/users", userToken);
        assertEquals(403, forbidden.status());
        assertEquals("FORBIDDEN", forbidden.code());

        Response allowed = get("/admin/users", loginAsAdmin());
        assertEquals(200, allowed.status());
        assertTrue(allowed.body().get("items").isArray());
    }

    @Test
    void internalApiRequiresTheSharedToken() {
        assertEquals(403, get("/internal/search/bootstrap", null).status());
        assertEquals(403, exchange("GET", "/internal/search/bootstrap", null, null,
                Map.of("X-Internal-Token", "wrong")).status());
        // Ni siquiera un admin con JWT entra: es una API servidor a servidor.
        assertEquals(403, get("/internal/search/bootstrap", loginAsAdmin()).status());

        Response ok = exchange("GET", "/internal/search/bootstrap", null, null,
                Map.of("X-Internal-Token", INTERNAL_TOKEN));
        assertEquals(200, ok.status());
        assertTrue(ok.body().get("apiKeys").isArray());
        assertTrue(ok.body().get("embeddingModels").isArray());
    }

    @Test
    void webhookRequiresAPerTrainingTokenAndFailsClosed() {
        String body = "{\"training_id\":1,\"list_id\":1,\"status\":\"training\"}";
        assertEquals(403, post("/webhooks/training-update", body, null).status());
        for (String token : new String[]{"wrong", INTERNAL_TOKEN, WEBHOOK_SECRET}) {
            assertEquals(403, exchange("POST", "/webhooks/training-update", body, null,
                    Map.of("X-Webhook-Token", token)).status(), "token=" + token);
        }
        // Un token válido de OTRO entrenamiento no puede reportar sobre este.
        Response foreign = exchange("POST", "/webhooks/training-update", body, null,
                Map.of("X-Webhook-Token", WebhookTokens.issue(WEBHOOK_SECRET, 2)));
        assertEquals(403, foreign.status());
        assertEquals("WEBHOOK_TOKEN_MISMATCH", foreign.code());
        // El token del propio run pasa el filtro y el controlador (el training 1 no existe: 404).
        Response own = exchange("POST", "/webhooks/training-update", body, null,
                Map.of("X-Webhook-Token", WebhookTokens.issue(WEBHOOK_SECRET, 1)));
        assertEquals(404, own.status());
    }
}
