package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code rememberDevice}: devolver un token de confianza para no pedir el código durante {@code mfaTrustDays}. */
public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code, Boolean rememberDevice) {
}
