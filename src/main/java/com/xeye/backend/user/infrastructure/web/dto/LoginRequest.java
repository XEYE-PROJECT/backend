package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        String captchaToken,
        /** Token de "recordar este dispositivo" devuelto por POST /auth/mfa (opcional). */
        String mfaTrustToken) {
}
