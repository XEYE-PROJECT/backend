package com.xeye.backend.shared.security;

import java.time.Instant;

/**
 * El llamante autenticado, derivado de un JWT válido y expuesto a los controladores vía
 * {@code @AuthenticationPrincipal AuthenticatedUser}. Sin acceso a BD: todo sale de los claims.
 * {@code jti}/{@code expiresAt} identifican el token concreto (para revocarlo en el logout).
 */
public record AuthenticatedUser(Long id, String email, String permission, String jti, Instant expiresAt) {

    public AuthenticatedUser(Long id, String email, String permission) {
        this(id, email, permission, null, null);
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(permission);
    }
}
