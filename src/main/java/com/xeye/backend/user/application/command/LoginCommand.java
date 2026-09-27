package com.xeye.backend.user.application.command;

/** {@code mfaTrustToken}: token de "recordar este dispositivo" de un 2FA anterior (opcional). */
public record LoginCommand(String email, String rawPassword, String captchaToken, String mfaTrustToken) {
}
