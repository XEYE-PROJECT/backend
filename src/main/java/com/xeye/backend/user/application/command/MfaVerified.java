package com.xeye.backend.user.application.command;

/** Segundo factor superado: la sesión más, si se pidió recordar el dispositivo, su token de confianza. */
public record MfaVerified(AuthResult auth, String mfaTrustToken) {
}
