package com.xeye.backend.user.domain.model;

import java.time.Instant;

/**
 * Token de un solo uso (verificar email, recuperar contraseña, confirmar cambio de email).
 * Solo se persiste su hash SHA-256; el valor en claro viaja una vez, dentro del enlace del email.
 * {@code payload} guarda datos del flujo (el nuevo email en CHANGE_EMAIL).
 */
public record UserToken(Long id, Long userId, TokenPurpose purpose, String tokenHash, String payload,
                        Instant expiresAt, Instant usedAt, Instant createdAt) {

    public static UserToken issue(Long userId, TokenPurpose purpose, String tokenHash, String payload, Instant now) {
        return new UserToken(null, userId, purpose, tokenHash, payload, now.plus(purpose.validity()), null, null);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public UserToken markUsed(Instant now) {
        return new UserToken(id, userId, purpose, tokenHash, payload, expiresAt, now, createdAt);
    }
}
