package com.xeye.backend.search.application.command;

import java.time.Instant;

/** Una entrada de log tal como la reporta el microservicio de búsqueda (results ya serializado a JSON). */
public record RecordSearchCommand(
        Long userId,
        Long apiKeyId,
        Long listId,
        String listName,
        String endpoint,
        String searchTerm,
        Integer totalResults,
        Integer durationMs,
        String session,
        String results,
        Instant searchedAt) {
}
