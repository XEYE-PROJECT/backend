package com.xeye.backend.user.application.command;

/** Login a medias: la contraseña era correcta pero la cuenta tiene 2FA; falta el código TOTP. */
public record MfaChallenge(String mfaToken) implements LoginOutcome {
}
