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
 */
public class Element {

    private final Long id;
    private final Long listId;
    private String text;
    private String params;
    private String description;
    private String generatedDescription;
    private boolean trained;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Element(Long id, Long listId, String text, String params, String description,
                   String generatedDescription, boolean trained, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.listId = Objects.requireNonNull(listId, "listId");
        this.text = requireText(text);
        this.params = params;
        this.description = description;
        this.generatedDescription = generatedDescription;
        this.trained = trained;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Element create(Long listId, String text, String params, String description) {
        return new Element(null, listId, text, params, description, null, false, null, null);
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
        if (Objects.equals(this.description, description)) {
            return false;
        }
        this.description = description;
        this.trained = false;
        this.generatedDescription = null;
        return true;
    }

    /** Devuelve true si los params cambiaron (los devuelve la búsqueda, así que search debe recargar). */
    public boolean changeParams(String params) {
        if (Objects.equals(this.params, params)) {
            return false;
        }
        this.params = params;
        return true;
    }

    public void markTrained(boolean trained) {
        this.trained = trained;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Element text must not be blank");
        }
        return value.trim();
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
