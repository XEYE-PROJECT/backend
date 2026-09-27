package com.xeye.backend.shared.security;

import com.xeye.backend.shared.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Rate limit por IP de los endpoints públicos de autenticación ({@code POST /auth/*}), antes de
 * tocar la BD o BCrypt. Responde 429 JSON con {@code Retry-After}. El bloqueo progresivo por
 * cuenta (independiente de la IP) lo aplica el servicio de login. La IP es
 * {@code getRemoteAddr()}: en producción Tomcat ya la ha sustituido por la del
 * {@code X-Forwarded-For} que pone Caddy ({@code server.forward-headers-strategy: native}).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, InMemoryRateLimiter> limitersByPath;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(Map<String, InMemoryRateLimiter> limitersByPath, ObjectMapper objectMapper) {
        this.limitersByPath = limitersByPath;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !limitersByPath.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        InMemoryRateLimiter limiter = limitersByPath.get(request.getRequestURI());
        String ip = request.getRemoteAddr();
        long retryAfter = limiter.tryAcquire(ip, Instant.now());
        if (retryAfter > 0) {
            AuthAuditLog.warn("rate_limited", ip, null, "path=" + request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many requests, try again in " + retryAfter + " seconds", "RATE_LIMITED"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
