package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
}
