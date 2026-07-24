package com.xeye.backend.shared.event;

/**
 * Publicado por el módulo user al borrar la cuenta. La BD cascadea api_keys/listas/elementos;
 * este evento permite a búsqueda descartar de una vez todo lo que cachea del usuario.
 */
public record UserDeletedEvent(Long userId) {
}
