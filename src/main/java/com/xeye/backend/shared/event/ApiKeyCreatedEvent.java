package com.xeye.backend.shared.event;

/** Publicado por el módulo apikey para que búsqueda acepte la nueva clave de inmediato. */
public record ApiKeyCreatedEvent(Long apiKeyId, Long userId, String apiKey) {
}
