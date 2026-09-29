package com.xeye.backend.shared.security;

import com.xeye.backend.shared.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica las llamadas servidor-a-servidor por un token en cabecera: la API interna del
 * search-service ({@code X-Internal-Token}, secreto compartido tal cual) y el webhook del worker
 * de training ({@code X-Webhook-Token}, token por entrenamiento derivado del secreto con HMAC,
 * ver {@link WebhookTokens}). Solo actúa sobre las rutas de {@code matcher}: si la cabecera falta
 * o el {@link TokenVerifier} no la acepta (comparación en tiempo constante) responde 403 y corta
 * la cadena; si la acepta deja el {@link ServicePrincipal} devuelto con el rol indicado, que
 * {@link SecurityConfig} exige en esas rutas. Falla cerrado: un secreto configurado en blanco
 * rechaza todo.
 * <p>
 * No es {@code @Component} a propósito (igual que {@link JwtAuthenticationFilter}): se cablea
 * en {@link SecurityConfig} para no registrarlo también en la cadena normal del servlet.
 */
public class SharedSecretAuthenticationFilter extends OncePerRequestFilter {

    /** Decide si el valor de la cabecera autentica y a quién. Vacío = 403. */
    @FunctionalInterface
    public interface TokenVerifier {
        Optional<ServicePrincipal> verify(String providedToken);
    }

    private final RequestMatcher matcher;
    private final String headerName;
    private final TokenVerifier verifier;
    private final String authority;
    private final String failureMessage;
    private final ObjectMapper objectMapper;

    public SharedSecretAuthenticationFilter(RequestMatcher matcher, String headerName, TokenVerifier verifier,
                                            String role, String failureMessage, ObjectMapper objectMapper) {
        this.matcher = matcher;
        this.headerName = headerName;
        this.verifier = verifier;
        this.authority = "ROLE_" + role;
        this.failureMessage = failureMessage;
        this.objectMapper = objectMapper;
    }

    /** Secreto compartido tal cual en la cabecera (API interna del search-service). */
    public static TokenVerifier sharedSecret(String expectedSecret, ServicePrincipal principal) {
        return provided -> SecretTokens.constantTimeEquals(expectedSecret, provided)
                ? Optional.of(principal) : Optional.empty();
    }

    /** Token por entrenamiento firmado con HMAC del secreto (webhook del worker de training). */
    public static TokenVerifier perTrainingToken(String secret, String principalName) {
        return provided -> WebhookTokens.verify(secret, provided)
                .map(trainingId -> new ServicePrincipal(principalName, trainingId));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<ServicePrincipal> principal = verifier.verify(request.getHeader(headerName));
        if (principal.isEmpty()) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                    HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(), failureMessage));
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                principal.get(), null, List.of(new SimpleGrantedAuthority(authority)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
