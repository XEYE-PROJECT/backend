package com.xeye.backend.shared.event;

/**
 * Publicado por el módulo list cuando cambian metadatos que usa búsqueda (nombre o
 * visibilidad; la descripción solo importa para training). El módulo search lo reenvía
 * al microservicio tras el commit.
 */
public record ListMetaChangedEvent(Long listId, Long userId, String name, boolean isPublic) {
}
