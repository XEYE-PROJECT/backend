package com.xeye.backend.user.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Política de contraseñas (dominio puro). Longitud mínima, máximo de 72 bytes (límite real de
 * BCrypt: lo que sobra se ignoraría en silencio), lista corta de contraseñas trivialmente
 * comunes y que no contenga el nombre de usuario del email. La comprobación contra filtraciones
 * (HIBP) es un puerto de aplicación porque necesita red.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "p@ssw0rd", "contraseña", "contrasena",
            "12345678", "123456789", "1234567890", "qwertyuiop", "qwerty123", "abc12345", "abcd1234",
            "11111111", "00000000", "iloveyou", "welcome1", "letmein1", "admin123", "administrator",
            "sunshine", "princess", "football", "baseball", "superman", "trustno1", "dragon123",
            "monkey123", "master123", "shadow123", "michael1", "jennifer", "computer", "internet",
            "whatever", "starwars", "pokemon1", "hello123", "charlie1", "aa123456", "asdfghjkl",
            "zxcvbnm1", "1q2w3e4r", "1qaz2wsx", "qazwsx123", "12341234", "87654321", "password!",
            "changeme", "changeme1", "temp1234", "test1234", "welcome123", "adminadmin", "rootroot",
            "xeye1234", "admin1234");

    private PasswordPolicy() {
    }

    /** @return el motivo de rechazo, o vacío si la contraseña cumple la política. */
    public static Optional<String> validate(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            return Optional.of("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Password must be at most " + MAX_BYTES + " bytes");
        }
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return Optional.of("Password is too common");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (local.length() >= 4 && lower.contains(local)) {
                return Optional.of("Password must not contain your email");
            }
        }
        return Optional.empty();
    }
}
