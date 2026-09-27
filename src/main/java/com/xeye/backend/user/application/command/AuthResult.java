package com.xeye.backend.user.application.command;

import com.xeye.backend.user.domain.model.User;

/** Sesión abierta: el usuario más un token de acceso recién emitido. */
public record AuthResult(User user, String token, long expiresInMinutes) implements LoginOutcome {
}
