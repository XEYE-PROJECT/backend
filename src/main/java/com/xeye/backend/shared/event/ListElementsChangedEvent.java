package com.xeye.backend.shared.event;

/**
 * Publicado por el módulo element ante cualquier mutación de elementos. Búsqueda invalida su
 * copia cacheada de la lista y la recarga perezosamente, de modo que los resultados reflejan
 * textos/params frescos al momento — incluso antes de que complete un reentrenamiento.
 */
public record ListElementsChangedEvent(Long listId, Long userId) {
}
