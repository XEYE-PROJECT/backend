package com.xeye.backend.shared.security;

/**
 * Consulta de revocación que aplica {@link JwtAuthenticationFilter} a cada token válido:
 * lista negra por {@code jti} (logout de un token) y versión por usuario (cerrar todas las
 * sesiones, cambio de contraseña/email). La implementación vive en el módulo {@code user}.
 */
public interface TokenRevocationRegistry {

    boolean isRevoked(String jti);

    /** @return true si {@code version} es la versión de tokens vigente del usuario. */
    boolean isCurrentVersion(Long userId, int version);
}
