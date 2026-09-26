package com.xeye.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Comparación de secretos compartidos en tiempo constante. Se comparan los SHA-256 de ambos
 * valores: {@link MessageDigest#isEqual} solo es de tiempo constante con longitudes iguales, y
 * hashear antes evita además filtrar la longitud del secreto real por el tiempo de respuesta.
 */
public final class SecretTokens {

    private SecretTokens() {
    }

    /**
     * {@code true} solo si ambos valores existen y coinciden. Un secreto esperado en blanco
     * devuelve siempre {@code false} (fallo cerrado: nunca "sin secreto = todo permitido").
     */
    public static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(expected), sha256(provided));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
