package com.xeye.backend.shared.event;

/** Publicado por el módulo apikey para que búsqueda deje de aceptar la clave borrada. */
public record ApiKeyDeletedEvent(Long apiKeyId, Long userId) {
}
