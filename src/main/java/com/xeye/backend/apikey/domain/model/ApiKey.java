package com.xeye.backend.apikey.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Agregado ApiKey. El valor secreto se guarda en claro (el search-service lo cachea). */
public class ApiKey {

    private final Long id;
    private final Long userId;
    private String name;
    private final String apiKey;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ApiKey(Long id, Long userId, String name, String apiKey, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = requireText(name);
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ApiKey create(Long userId, String name, String apiKey) {
        return new ApiKey(null, userId, name, apiKey, null, null);
    }

    public void rename(String name) {
        this.name = requireText(name);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }
        return value.trim();
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public String apiKey() {
        return apiKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
