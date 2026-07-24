package com.xeye.backend.apikey.infrastructure.web.dto;

import com.xeye.backend.apikey.domain.model.ApiKey;

import java.time.Instant;

public record ApiKeyResponse(
        Long id,
        String name,
        String apiKey,
        Instant createdAt,
        Instant updatedAt) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.id(),
                apiKey.name(),
                apiKey.apiKey(),
                apiKey.createdAt(),
                apiKey.updatedAt());
    }
}
