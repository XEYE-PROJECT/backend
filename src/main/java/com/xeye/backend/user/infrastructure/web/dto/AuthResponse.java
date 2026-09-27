package com.xeye.backend.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.MfaVerified;

/**
 * Sesión abierta (sin paso de 2FA pendiente). {@code mfaTrustToken} solo viene tras pasar el 2FA
 * con "recordar este dispositivo": el cliente lo guarda y lo envía en los siguientes logins.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, String tokenType, long expiresInMinutes, UserResponse user,
                           String mfaTrustToken) {

    public static AuthResponse of(AuthResult result) {
        return new AuthResponse(result.token(), "Bearer", result.expiresInMinutes(), UserResponse.from(result.user()), null);
    }

    public static AuthResponse of(MfaVerified verified) {
        AuthResult result = verified.auth();
        return new AuthResponse(result.token(), "Bearer", result.expiresInMinutes(), UserResponse.from(result.user()),
                verified.mfaTrustToken());
    }
}
