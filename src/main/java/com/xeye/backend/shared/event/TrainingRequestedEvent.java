package com.xeye.backend.shared.event;

/**
 * Publicado por los módulos list/element cuando cambia contenido buscable (descripción de la
 * lista, texto/descripción de un elemento, altas o bajas). El módulo training lo escucha tras
 * el commit. Vive en {@code shared} para que publicadores y listener dependan solo de shared,
 * sin ciclos de compilación entre módulos.
 */
public record TrainingRequestedEvent(Long listId, Long userId, String reason) {
}
