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

/**
 * Autentica las llamadas servidor-a-servidor por secreto compartido en una cabecera: el
 * webhook del worker de training ({@code X-Webhook-Token}) y la API interna del search-service
 * ({@code X-Internal-Token}). Solo actúa sobre las rutas de {@code matcher}: si la cabecera
 * falta o no coincide (comparación en tiempo constante) responde 403 y corta la cadena; si
 * coincide deja un {@link ServicePrincipal} con el rol indicado, que {@link SecurityConfig}
 * exige en esas rutas. Falla cerrado: un secreto configurado en blanco rechaza todo.
 * <p>
 * No es {@code @Component} a propósito (igual que {@link JwtAuthenticationFilter}): se cablea
 * en {@link SecurityConfig} para no registrarlo también en la cadena normal del servlet.
 */
public class SharedSecretAuthenticationFilter extends OncePerRequestFilter {

    private final RequestMatcher matcher;
    private final String headerName;
    private final String expectedSecret;
    private final ServicePrincipal principal;
    private final String authority;
    private final String failureMessage;
    private final ObjectMapper objectMapper;

    public SharedSecretAuthenticationFilter(RequestMatcher matcher, String headerName, String expectedSecret,
                                            ServicePrincipal principal, String role, String failureMessage,
                                            ObjectMapper objectMapper) {
        this.matcher = matcher;
        this.headerName = headerName;
        this.expectedSecret = expectedSecret;
        this.principal = principal;
        this.authority = "ROLE_" + role;
        this.failureMessage = failureMessage;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(headerName);
        if (!SecretTokens.constantTimeEquals(expectedSecret, provided)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                    HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(), failureMessage));
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
