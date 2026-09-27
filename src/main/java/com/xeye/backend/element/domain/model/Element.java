package com.xeye.backend.element.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Elemento de una lista. {@code trained} indica si el text/description actual está reflejado
 * en el último entrenamiento completado: cambiar {@code text} o {@code description} lo pone a
 * false (los métodos de cambio devuelven si toca reentrenar); {@code params} no afecta.
 *
 * <p>{@code generatedDescription} es la caché del enriquecimiento LLM del worker; se invalida
 * (null) justo cuando cambian text o description, sus dos únicas entradas.
 * {@code version} es el contador de bloqueo optimista de la fila (null en elementos nuevos).
 */
public class Element {

    public static final int MAX_TEXT_LENGTH = 1000;
    public static final int MAX_DESCRIPTION_LENGTH = 4000;
    public static final int MAX_PARAMS_LENGTH = 8000;

    private final Long id;
    private final Long listId;
    private String text;
    private String params;
    private String description;
    private String generatedDescription;
    private boolean trained;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Element(Long id, Long listId, String text, String params, String description,
                   String generatedDescription, boolean trained, Instant createdAt, Instant updatedAt) {
        this(id, listId, text, params, description, generatedDescription, trained, null, createdAt, updatedAt);
    }

    public Element(Long id, Long listId, String text, String params, String description,
                   String generatedDescription, boolean trained, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.listId = Objects.requireNonNull(listId, "listId");
        this.text = requireText(text);
        this.params = requireMax("Element params", params, MAX_PARAMS_LENGTH);
        this.description = requireMax("Element description", description, MAX_DESCRIPTION_LENGTH);
        this.generatedDescription = generatedDescription;
        this.trained = trained;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Element create(Long listId, String text, String params, String description) {
        return new Element(null, listId, text, params, description, null, false, null, null, null);
    }

    /** Devuelve true si el texto cambió de verdad (y por tanto toca reentrenar). */
    public boolean changeText(String text) {
        String normalized = requireText(text);
        if (normalized.equals(this.text)) {
            return false;
        }
        this.text = normalized;
        this.trained = false;
        this.generatedDescription = null;
        return true;
    }

    /** Devuelve true si la descripción cambió de verdad (y por tanto toca reentrenar). */
    public boolean changeDescription(String description) {
        String normalized = requireMax("Element description", description, MAX_DESCRIPTION_LENGTH);
        if (Objects.equals(this.description, normalized)) {
            return false;
        }
        this.description = normalized;
        this.trained = false;
        this.generatedDescription = null;
        return true;
    }

    /** Devuelve true si los params cambiaron (los devuelve la búsqueda, así que search debe recargar). */
    public boolean changeParams(String params) {
        String normalized = requireMax("Element params", params, MAX_PARAMS_LENGTH);
        if (Objects.equals(this.params, normalized)) {
            return false;
        }
        this.params = normalized;
        return true;
    }

    public void markTrained(boolean trained) {
        this.trained = trained;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Element text must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Element text must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String requireMax(String what, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(what + " must be at most " + max + " characters");
        }
        return value;
    }

    public Long id() {
        return id;
    }

    public Long listId() {
        return listId;
    }

    public String text() {
        return text;
    }

    public String params() {
        return params;
    }

    public String description() {
        return description;
    }

    public String generatedDescription() {
        return generatedDescription;
    }

    public boolean trained() {
        return trained;
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
