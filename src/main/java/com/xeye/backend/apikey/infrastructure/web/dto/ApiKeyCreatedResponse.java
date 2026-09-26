package com.xeye.backend.apikey.infrastructure.web.dto;

import com.xeye.backend.apikey.application.port.in.ApiKeyUseCases.CreatedApiKey;

import java.time.Instant;

/**
 * Respuesta de {@code POST /api-keys}: la única vez que el cliente ve {@code apiKey} en claro.
 * Después solo existe su hash, así que hay que copiarla en ese momento.
 */
public record ApiKeyCreatedResponse(
        Long id,
        String name,
        String prefix,
        String apiKey,
        Instant createdAt,
        Instant updatedAt) {

    public static ApiKeyCreatedResponse from(CreatedApiKey created) {
        return new ApiKeyCreatedResponse(
                created.apiKey().id(),
                created.apiKey().name(),
                created.apiKey().prefix(),
                created.rawKey(),
                created.apiKey().createdAt(),
                created.apiKey().updatedAt());
    }
}
