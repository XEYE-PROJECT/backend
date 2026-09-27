package com.xeye.backend.search.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xeye.backend.search.application.port.out.SearchQueryGateway.SearchQueryResult;

import java.util.List;

/** Resultado del playground, en camelCase como el resto del backend (el buscador habla snake_case). */
public record ConsoleSearchResponse(List<Result> results, int totalResults, String searchTerm, String listName,
                                    int durationMs) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(String item, double score, Object params, Double textScore, Double semanticScore) {
    }

    public static ConsoleSearchResponse from(SearchQueryResult result) {
        List<Result> results = result.results().stream()
                .map(h -> new Result(h.item(), h.score(), h.params(), h.textScore(), h.semanticScore()))
                .toList();
        return new ConsoleSearchResponse(results, result.totalResults(), result.searchTerm(), result.listName(),
                result.durationMs());
    }
}
