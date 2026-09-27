package com.xeye.backend.shared.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Lee {@code Authorization: Bearer <jwt>}, lo valida (firma, caducidad, propósito {@code access},
 * no revocado, versión de sesión vigente) y deja un {@link AuthenticatedUser} en el
 * SecurityContext; los tokens inválidos se registran en el log de auditoría y las rutas
 * protegidas acaban en 401. No es {@code @Component} a propósito: se cablea en
 * {@link SecurityConfig} para no registrarlo también en la cadena de filtros normal del servlet.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenRevocationRegistry revocations;

    public JwtAuthenticationFilter(JwtService jwtService, TokenRevocationRegistry revocations) {
        this.jwtService = jwtService;
        this.revocations = revocations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String ip = request.getRemoteAddr();
            try {
                JwtService.ParsedToken parsed = jwtService.parse(header.substring(7), JwtService.PURPOSE_ACCESS);
                if (revocations.isRevoked(parsed.jti())) {
                    AuthAuditLog.warn("token_revoked", ip, parsed.email(), "jti=" + parsed.jti());
                    SecurityContextHolder.clearContext();
                } else if (!revocations.isCurrentVersion(Long.valueOf(parsed.subject()), parsed.version())) {
                    AuthAuditLog.info("token_stale_version", ip, parsed.email(), "ver=" + parsed.version());
                    SecurityContextHolder.clearContext();
                } else {
                    AuthenticatedUser user = parsed.toAuthenticatedUser();
                    String role = "ROLE_" + (user.permission() == null ? "USER"
                            : user.permission().toUpperCase(Locale.ROOT));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority(role)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                // Normal al caducar la sesión: nivel info para no llenar el log de avisos.
                AuthAuditLog.info("token_expired", ip, e.getClaims().get("email", String.class), null);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException e) {
                AuthAuditLog.warn("token_invalid", ip, null, "reason=" + e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
