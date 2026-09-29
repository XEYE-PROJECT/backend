package com.xeye.backend.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SharedSecretAuthenticationFilterTest {

    private static final String SECRET = "a-long-shared-secret-for-the-tests-0123456789";

    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Modo secreto compartido (API interna), montado sobre la ruta del webhook para reutilizar las peticiones. */
    private SharedSecretAuthenticationFilter filter(String configuredSecret) {
        return new SharedSecretAuthenticationFilter(
                PathPatternRequestMatcher.withDefaults().matcher("/webhooks/**"),
                "X-Webhook-Token",
                SharedSecretAuthenticationFilter.sharedSecret(configuredSecret, new ServicePrincipal("training-worker")),
                "TRAINING_WORKER", "Invalid webhook token", new ObjectMapper());
    }

    /** Modo token por entrenamiento (webhook del worker). */
    private SharedSecretAuthenticationFilter perTrainingFilter(String configuredSecret) {
        return new SharedSecretAuthenticationFilter(
                PathPatternRequestMatcher.withDefaults().matcher("/webhooks/**"),
                "X-Webhook-Token",
                SharedSecretAuthenticationFilter.perTrainingToken(configuredSecret, "training-worker"),
                "TRAINING_WORKER", "Invalid webhook token", new ObjectMapper());
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) {
            request.addHeader("X-Webhook-Token", token);
        }
        return request;
    }

    @Test
    void validTokenAuthenticatesTheServiceWithItsRole() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(SECRET).doFilter(request("/webhooks/training-update", SECRET), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(new ServicePrincipal("training-worker"), authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRAINING_WORKER")));
    }

    @Test
    void missingOrWrongTokenIsRejectedWith403BeforeTheController() throws Exception {
        for (String token : new String[]{null, "", "wrong", SECRET + "x"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter(SECRET).doFilter(request("/webhooks/training-update", token), response, chain);

            assertEquals(403, response.getStatus(), "token=" + token);
            assertTrue(response.getContentAsString().contains("Invalid webhook token"));
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blankConfiguredSecretFailsClosed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter("   ").doFilter(request("/webhooks/training-update", "   "), response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void perTrainingTokenAuthenticatesTheWorkerForThatTraining() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        perTrainingFilter(SECRET).doFilter(
                request("/webhooks/training-update", WebhookTokens.issue(SECRET, 42)), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(new ServicePrincipal("training-worker", 42L), authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRAINING_WORKER")));
    }

    @Test
    void theRawSecretNoLongerOpensTheWebhook() throws Exception {
        // El secreto solo vive en el backend: en la cabecera solo valen tokens derivados de él.
        for (String token : new String[]{SECRET, "42." + SECRET, "42.", "wrong"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            perTrainingFilter(SECRET).doFilter(request("/webhooks/training-update", token), response, chain);

            assertEquals(403, response.getStatus(), "token=" + token);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherRoutesAreLeftUntouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(SECRET).doFilter(request("/lists", null), response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
