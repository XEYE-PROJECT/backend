package com.xeye.backend.list.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Lista de elementos de un usuario (se llama {@code ItemList} para no chocar con
 * {@code java.util.List}). Su {@code description} es contexto de entrenamiento de toda la
 * lista: cambiarla dispara un reentrenamiento (gestionado en la capa de aplicación).
 */
public class ItemList {

    private final Long id;
    private String name;
    private String description;
    private boolean isPublic;
    private final Long userId;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ItemList(Long id, String name, String description, boolean isPublic, Long userId,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = requireText(name);
        this.description = description;
        this.isPublic = isPublic;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ItemList create(Long userId, String name, String description, boolean isPublic) {
        return new ItemList(null, name, description, isPublic, userId, null, null);
    }

    public void rename(String name) {
        this.name = requireText(name);
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("List name must not be blank");
        }
        return value.trim();
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
