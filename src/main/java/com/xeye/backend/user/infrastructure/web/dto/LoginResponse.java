package com.xeye.backend.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginOutcome;
import com.xeye.backend.user.application.command.MfaChallenge;

/**
 * Respuesta de login: o bien la sesión ({@code token} + {@code user}), o bien
 * {@code mfaRequired=true} con el {@code mfaToken} que hay que devolver en {@code POST /auth/mfa}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(boolean mfaRequired, String mfaToken, String token, String tokenType,
                            Long expiresInMinutes, UserResponse user) {

    public static LoginResponse of(LoginOutcome outcome) {
        if (outcome instanceof MfaChallenge challenge) {
            return new LoginResponse(true, challenge.mfaToken(), null, null, null, null);
        }
        AuthResult result = (AuthResult) outcome;
        return new LoginResponse(false, null, result.token(), "Bearer", result.expiresInMinutes(),
                UserResponse.from(result.user()));
    }
}
