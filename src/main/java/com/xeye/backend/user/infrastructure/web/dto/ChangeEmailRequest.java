package com.xeye.backend.user.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(@NotBlank @Email String email, @NotBlank String currentPassword) {
}
