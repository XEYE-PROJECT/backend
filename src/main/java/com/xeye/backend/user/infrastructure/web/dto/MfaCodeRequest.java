package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaCodeRequest(@NotBlank String code) {
}
