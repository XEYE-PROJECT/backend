package com.xeye.backend.search.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Lote de logs de búsqueda reportado por el microservicio de búsqueda. */
public record IngestSearchLogsRequest(List<Entry> logs) {

    public record Entry(
            Long userId,
            Long apiKeyId,
            Long listId,
            String listName,
            String endpoint,
            String searchTerm,
            Integer totalResults,
            Integer durationMs,
            String session,
            Map<String, Double> results,
            Instant searchedAt) {
    }
}
