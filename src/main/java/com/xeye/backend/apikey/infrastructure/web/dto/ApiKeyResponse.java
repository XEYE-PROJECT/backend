package com.xeye.backend.apikey.infrastructure.web.dto;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.time.Instant;

/** Vista de una clave existente: nunca incluye el valor en claro (solo el prefijo identificativo). */
public record ApiKeyResponse(
        Long id,
        String name,
        String prefix,
        Instant createdAt,
        Instant updatedAt) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.id(),
                apiKey.name(),
                apiKey.prefix(),
                apiKey.createdAt(),
                apiKey.updatedAt());
    }
}
