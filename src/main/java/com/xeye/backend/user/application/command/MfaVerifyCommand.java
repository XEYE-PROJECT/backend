package com.xeye.backend.user.application.command;

/** Segundo paso del login: el token efímero del primer paso más el código TOTP (o uno de recuperación). */
public record MfaVerifyCommand(String mfaToken, String code, boolean rememberDevice) {
}
