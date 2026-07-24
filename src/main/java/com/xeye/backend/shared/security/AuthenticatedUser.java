package com.xeye.backend.shared.security;

/**
 * El llamante autenticado, derivado de un JWT válido y expuesto a los controladores vía
 * {@code @AuthenticationPrincipal AuthenticatedUser}. Sin acceso a BD: todo sale de los claims.
 */
public record AuthenticatedUser(Long id, String email, String permission) {

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(permission);
    }
}
