package com.xeye.backend.user.application.port.out;

import java.time.Instant;

/**
 * Revocación de sesiones: lista negra de un token concreto (logout) y aviso de que la versión
 * de tokens de un usuario cambió (cerrar todas las sesiones). El adaptador es a la vez el
 * {@code TokenRevocationRegistry} que consulta el filtro JWT.
 */
public interface SessionRevoker {

    void revoke(String jti, Long userId, Instant expiresAt);

    /** El {@code token_version} del usuario acaba de cambiar (invalida cualquier caché). */
    void versionChanged(Long userId);

    int deleteExpiredBefore(Instant cutoff);
}
