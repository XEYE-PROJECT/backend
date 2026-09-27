package com.xeye.backend.search.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /lists/{listId}/search} (mismos límites que la API pública del buscador). */
public record ConsoleSearchRequest(
        @NotBlank @Size(max = 500) String searchTerm,
        @Min(1) @Max(1000) Integer limit,
        Boolean includeScoreBreakdown) {

    public int limitOrDefault() {
        return limit == null ? 50 : limit;
    }

    public boolean includeScoreBreakdownOrDefault() {
        return Boolean.TRUE.equals(includeScoreBreakdown);
    }
}
