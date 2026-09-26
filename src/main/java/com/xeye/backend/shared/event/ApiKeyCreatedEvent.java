package com.xeye.backend.shared.event;

/**
 * Publicado por el módulo apikey para que búsqueda acepte la nueva clave de inmediato.
 * Lleva el SHA-256 de la clave, nunca su valor en claro.
 */
public record ApiKeyCreatedEvent(Long apiKeyId, Long userId, String keyHash) {
}
