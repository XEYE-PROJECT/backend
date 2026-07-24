package com.xeye.backend.training.application.command;

import java.util.List;

/**
 * Payload enviado a búsqueda al completarse un training. {@code model} es el string opaco del
 * worker (aquí no se parsea), para que búsqueda embeba las queries con el mismo modelo.
 * {@code trainedElementIds} son los ids capturados al lanzar (id ASC): la fila i de la matriz
 * de embeddings pertenece a trainedElementIds[i], aunque los elementos cambiaran durante el run.
 */
public record SearchIndexCommand(
        Long listId,
        Long userId,
        String listName,
        boolean isPublic,
        String embeddingsData,
        String model,
        List<Long> trainedElementIds,
        List<Element> elements) {

    public record Element(Long id, String text, String params, String description, String generatedDescription) {
    }
}
