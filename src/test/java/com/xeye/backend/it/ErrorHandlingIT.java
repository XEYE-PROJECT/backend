package com.xeye.backend.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code GlobalExceptionHandler} de extremo a extremo: todo error es un {@code ApiError} JSON con {@code code}. */
class ErrorHandlingIT extends AbstractIntegrationTest {

    @Test
    void unknownRouteIs404() {
        Response response = get("/no-such-endpoint", loginAsAdmin());
        assertEquals(404, response.status());
        assertEquals("NOT_FOUND", response.code());
    }

    @Test
    void wrongMethodIs405WithAllow() {
        Response response = exchange("DELETE", "/lists", null, loginAsAdmin(), Map.of());
        assertEquals(405, response.status());
        assertEquals("METHOD_NOT_ALLOWED", response.code());
        assertNotNull(response.headers().getAllow());
    }

    @Test
    void malformedJsonIs400() {
        Response response = post("/lists", "{\"name\": ", loginAsAdmin());
        assertEquals(400, response.status());
        assertEquals("MALFORMED_BODY", response.code());
    }

    @Test
    void beanValidationIs400WithFieldDetails() {
        Response response = post("/lists", Map.of("description", "sin nombre"), loginAsAdmin());
        assertEquals(400, response.status());
        assertEquals("VALIDATION_FAILED", response.code());
        assertTrue(response.body().get("details").has("name"), response.body().toString());
    }

    @Test
    void typeMismatchInPathIs400() {
        Response response = get("/lists/not-a-number", loginAsAdmin());
        assertEquals(400, response.status());
        assertEquals("INVALID_PARAMETER", response.code());
    }

    @Test
    void missingResourceIs404() {
        Response response = get("/lists/999999999", loginAsAdmin());
        assertEquals(404, response.status());
        assertEquals("NOT_FOUND", response.code());
    }

    @Test
    void unsupportedContentTypeIs415() {
        Response response = http().post().uri("/lists")
                .header("Authorization", "Bearer " + loginAsAdmin())
                .contentType(MediaType.TEXT_PLAIN).body("name=x")
                .exchange((request, resp) -> new Response(resp.getStatusCode().value(), resp.getHeaders(),
                        json.readTree(resp.getBody())));
        assertEquals(415, response.status());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.code());
    }

    @Test
    void errorBodyShape() {
        Response response = get("/lists/999999999", loginAsAdmin());
        for (String field : new String[] {"status", "error", "message", "code", "timestamp"}) {
            assertTrue(response.body().has(field), "ApiError." + field);
        }
        assertEquals(404, response.body().get("status").asInt());
    }
}
