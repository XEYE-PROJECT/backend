package com.xeye.backend.search.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Una llamada de búsqueda (o target) servida por el microservicio de búsqueda, persistida aquí
 * porque el backend es el dueño de la BD. Inmutable — los logs son solo de inserción.
 * {@code results} es un string JSON opaco; {@code searchedAt} es cuándo corrió la búsqueda
 * (la ingesta va por lotes, puede ir por detrás de {@code createdAt}).
 */
public record SearchLog(
        Long id,
        Long userId,
        Long apiKeyId,
        Long listId,
        String listName,
        String endpoint,
        String searchTerm,
        int totalResults,
        int durationMs,
        String session,
        String results,
        Instant searchedAt,
        Instant createdAt) {

    public SearchLog {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(searchTerm, "searchTerm");
        Objects.requireNonNull(searchedAt, "searchedAt");
    }

    public static SearchLog create(Long userId, Long apiKeyId, Long listId, String listName,
                                   String endpoint, String searchTerm, int totalResults, int durationMs,
                                   String session, String results, Instant searchedAt) {
        return new SearchLog(null, userId, apiKeyId, listId, listName, endpoint, searchTerm,
                totalResults, durationMs, session, results, searchedAt, null);
    }
}
