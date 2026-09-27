package com.xeye.backend.search.application.port.out;

import com.xeye.backend.search.application.command.ConsoleSearchCommand;

import java.util.List;

/**
 * Puerto de salida: ejecuta una búsqueda en el microservicio de búsqueda por su API interna
 * ({@code POST /v1/lists/{id}/search}, {@code X-Internal-Token}). Solo lo usa el playground de la
 * consola; las integraciones llaman al buscador directamente con su API key.
 * <p>
 * El adaptador traduce las respuestas del buscador a excepciones de dominio: 404 →
 * {@code NotFoundException}, 429 → {@code TooManyRequestsException} (con su {@code Retry-After}),
 * caído/no configurado → {@code ServiceUnavailableException}.
 */
public interface SearchQueryGateway {

    SearchQueryResult search(Long listId, ConsoleSearchCommand command);

    record SearchQueryResult(List<Hit> results, int totalResults, String searchTerm, String listName,
                             int durationMs) {
    }

    /** {@code params} es el JSON del elemento ya parseado (o null); los desgloses solo si se pidieron. */
    record Hit(String item, double score, Object params, Double textScore, Double semanticScore) {
    }
}
