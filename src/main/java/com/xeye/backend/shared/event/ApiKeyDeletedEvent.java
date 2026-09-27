package com.xeye.backend.shared.event;

/** Publicado por el módulo apikey para que búsqueda deje de aceptar la clave borrada. */
public record ApiKeyDeletedEvent(Long apiKeyId, Long userId) implements DomainEvent {

    public static final String TYPE = "API_KEY_DELETED";

    @Override
    public String type() {
        return TYPE;
    }
}
