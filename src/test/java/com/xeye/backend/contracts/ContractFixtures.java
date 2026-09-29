package com.xeye.backend.contracts;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Acceso a {@code src/test/resources/contracts/*.json}: los ejemplos canónicos de cada mensaje
 * entre servicios (ver el README de ese directorio). Los otros dos repos llevan copias idénticas.
 */
public final class ContractFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ContractFixtures() {
    }

    public static String read(String name) {
        try (InputStream in = ContractFixtures.class.getResourceAsStream("/contracts/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No contract fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static JsonNode tree(String name) {
        return MAPPER.readTree(read(name));
    }

    /** Serializa y vuelve a parsear: así 7 (int) y 7L comparan igual y el orden de claves no cuenta. */
    public static JsonNode normalize(Object value) {
        return MAPPER.readTree(MAPPER.writeValueAsString(value));
    }
}
