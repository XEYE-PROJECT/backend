package com.xeye.backend.list.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Lista de elementos de un usuario (se llama {@code ItemList} para no chocar con
 * {@code java.util.List}). Su {@code description} es contexto de entrenamiento de toda la
 * lista: cambiarla dispara un reentrenamiento (gestionado en la capa de aplicación).
 * {@code version} es el contador de bloqueo optimista de la fila (null en listas nuevas).
 */
public class ItemList {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final Long id;
    private String name;
    private String description;
    private boolean isPublic;
    private final Long userId;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ItemList(Long id, String name, String description, boolean isPublic, Long userId,
                    Instant createdAt, Instant updatedAt) {
        this(id, name, description, isPublic, userId, null, createdAt, updatedAt);
    }

    public ItemList(Long id, String name, String description, boolean isPublic, Long userId, Long version,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = requireText(name);
        this.description = normalizeDescription(description);
        this.isPublic = isPublic;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ItemList create(Long userId, String name, String description, boolean isPublic) {
        return new ItemList(null, name, description, isPublic, userId, null, null, null);
    }

    public void rename(String name) {
        this.name = requireText(name);
    }

    public void changeDescription(String description) {
        this.description = normalizeDescription(description);
    }

    public void changeVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("List name must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("List name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "List description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return value;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Long userId() {
        return userId;
    }

    public Long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
