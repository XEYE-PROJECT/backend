package com.xeye.backend.shared.event;

/**
 * Publicado por el módulo user cuando un admin cambia el cupo de búsquedas/minuto de una cuenta.
 * {@code rateLimitPerMinute} null = vuelve al valor por defecto del search-service.
 */
public record UserSearchLimitChangedEvent(Long userId, Integer rateLimitPerMinute) {
}
