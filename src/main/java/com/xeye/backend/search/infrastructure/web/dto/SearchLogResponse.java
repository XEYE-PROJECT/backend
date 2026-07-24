package com.xeye.backend.search.infrastructure.web.dto;

import com.xeye.backend.search.domain.model.SearchLog;

import java.time.Instant;

public record SearchLogResponse(
        Long id,
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

    public static SearchLogResponse from(SearchLog log) {
        return new SearchLogResponse(
                log.id(), log.listId(), log.listName(), log.endpoint(), log.searchTerm(),
                log.totalResults(), log.durationMs(), log.session(), log.results(),
                log.searchedAt(), log.createdAt());
    }
}
