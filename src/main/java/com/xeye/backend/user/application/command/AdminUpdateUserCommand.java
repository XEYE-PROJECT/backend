package com.xeye.backend.user.application.command;

import com.xeye.backend.user.domain.model.Permission;

/**
 * Cambios que un administrador puede aplicar a otra cuenta (los {@code null} no se tocan).
 * {@code searchRateLimitPerMinute} fija el cupo de búsquedas de la cuenta;
 * {@code resetSearchRateLimit} lo devuelve al valor por defecto del buscador.
 */
public record AdminUpdateUserCommand(Permission permission, Boolean emailVerified, Boolean unlock,
                                     Integer searchRateLimitPerMinute, Boolean resetSearchRateLimit) {

    public AdminUpdateUserCommand(Permission permission, Boolean emailVerified, Boolean unlock) {
        this(permission, emailVerified, unlock, null, null);
    }
}
