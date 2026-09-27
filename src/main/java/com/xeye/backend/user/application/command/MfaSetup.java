package com.xeye.backend.user.application.command;

/** Secreto TOTP recién generado (aún no activo) y su URI otpauth para el QR. */
public record MfaSetup(String secret, String otpauthUri) {
}
