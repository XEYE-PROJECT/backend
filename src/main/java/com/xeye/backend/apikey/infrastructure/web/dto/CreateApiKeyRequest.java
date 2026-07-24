package com.xeye.backend.apikey.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** El nombre es opcional; el servicio usa "API Key" por defecto. */
public record CreateApiKeyRequest(@Size(max = 150) String name) {
}
