package com.xeye.backend.user.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Agregado User, dominio puro (sin JPA/Spring). {@code password} es siempre un hash;
 * el hasheo es un puerto ({@code PasswordHasher}) de la capa de aplicación.
 */
public class User {

    private final Long id;
    private String name;
    private String surname;
    private String email;
    private String password;
    private Permission permission;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(Long id, String name, String surname, String email, String password,
                Permission permission, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = requireText(name, "name");
        this.surname = requireText(surname, "surname");
        this.email = normalizeEmail(email);
        this.password = Objects.requireNonNull(password, "password");
        this.permission = permission == null ? Permission.USER : permission;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factoría de un usuario estándar nuevo (aún sin persistir). */
    public static User register(String name, String surname, String email, String hashedPassword) {
        return new User(null, name, surname, email, hashedPassword, Permission.USER, null, null);
    }

    public void rename(String name, String surname) {
        this.name = requireText(name, "name");
        this.surname = requireText(surname, "surname");
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changePassword(String hashedPassword) {
        this.password = Objects.requireNonNull(hashedPassword, "password");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.trim().toLowerCase();
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String surname() {
        return surname;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public Permission permission() {
        return permission;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
