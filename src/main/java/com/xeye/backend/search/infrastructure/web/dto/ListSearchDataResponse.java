package com.xeye.backend.search.infrastructure.web.dto;

import java.util.List;

/**
 * Todo lo que el microservicio de búsqueda necesita para (re)construir una lista en memoria:
 * metadatos, elementos y, si hay un training completado {@code in_use}, sus embeddings, el
 * string opaco del modelo y los ids capturados al lanzar (mismo formato que el push del índice;
 * la fila i de la matriz de embeddings pertenece a trainedElementIds[i]).
 */
public record ListSearchDataResponse(
        Long id,
        Long userId,
        String name,
        boolean isPublic,
        String model,
        String embeddingsData,
        List<Long> trainedElementIds,
        List<ElementEntry> elements) {

    public record ElementEntry(Long id, String text, String params, String description) {
    }
}
