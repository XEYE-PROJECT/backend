package com.xeye.backend.apikey.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateApiKeyRequest(@NotBlank @Size(max = 150) String name) {
}
