package com.xeye.backend.apikey.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cómo se almacena y se busca una API key: SHA-256 hex en minúsculas del valor en claro.
 * Un hash rápido (y no bcrypt) es correcto aquí porque las claves son aleatorias de 192 bits
 * (imposibles de adivinar por fuerza bruta) y así la búsqueda por índice es directa.
 * <p>
 * El search-service calcula exactamente lo mismo sobre la cabecera {@code X-API-Key}, y la
 * migración V6 lo hizo en SQL con {@code SHA2(api_key, 256)}: cambiar esto rompe ambos.
 */
public final class ApiKeyHasher {

    /** {@code xeye_} + 7 caracteres: identifica la clave en la consola sin revelarla. */
    public static final int PREFIX_LENGTH = 12;

    private ApiKeyHasher() {
    }

    public static String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }

    public static String prefixOf(String rawKey) {
        return rawKey.length() <= PREFIX_LENGTH ? rawKey : rawKey.substring(0, PREFIX_LENGTH);
    }
}
