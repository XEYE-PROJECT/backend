package com.xeye.backend.user.infrastructure.web.dto;

import com.xeye.backend.user.application.command.AuthResult;

/** Sesión abierta (sin paso de 2FA pendiente). */
public record AuthResponse(String token, String tokenType, long expiresInMinutes, UserResponse user) {

    public static AuthResponse of(AuthResult result) {
        return new AuthResponse(result.token(), "Bearer", result.expiresInMinutes(), UserResponse.from(result.user()));
    }
}
