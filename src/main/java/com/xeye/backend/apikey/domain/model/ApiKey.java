package com.xeye.backend.apikey.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Agregado ApiKey. Solo guarda el SHA-256 (hex) de la clave y un prefijo para identificarla en
 * la consola; el valor completo se entrega una única vez al crearla y no puede recuperarse.
 */
public class ApiKey {

    private final Long id;
    private final Long userId;
    private String name;
    private final String keyHash;
    private final String prefix;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ApiKey(Long id, Long userId, String name, String keyHash, String prefix,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = requireText(name);
        this.keyHash = requireText(keyHash);
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Nueva clave a partir de su valor en claro; el valor no se conserva en el agregado. */
    public static ApiKey create(Long userId, String name, String rawKey) {
        return new ApiKey(null, userId, name, ApiKeyHasher.hash(rawKey), ApiKeyHasher.prefixOf(rawKey), null, null);
    }

    public void rename(String name) {
        this.name = requireText(name);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank");
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

    /** SHA-256 hex (minúsculas) del valor en claro. */
    public String keyHash() {
        return keyHash;
    }

    /** Primeros caracteres del valor en claro, para reconocer la clave sin revelarla. */
    public String prefix() {
        return prefix;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
