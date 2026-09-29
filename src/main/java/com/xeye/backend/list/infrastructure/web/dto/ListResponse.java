package com.xeye.backend.list.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.list.application.port.in.ListUseCases.ListedList;
import com.xeye.backend.list.domain.model.ItemList;

import java.time.Instant;

public record ListResponse(
        Long id,
        String name,
        String description,
        @JsonProperty("public") boolean isPublic,
        /** false = la lista renuncia a las descripciones generadas por un LLM (se entrena sin paso IA). */
        boolean llmEnrichment,
        Long userId,
        long elementCount,
        Instant createdAt,
        Instant updatedAt) {

    public static ListResponse from(ListedList listed) {
        return from(listed.list(), listed.elementCount());
    }

    public static ListResponse from(ItemList list, long elementCount) {
        return new ListResponse(
                list.id(),
                list.name(),
                list.description(),
                list.isPublic(),
                list.llmEnrichment(),
                list.userId(),
                elementCount,
                list.createdAt(),
                list.updatedAt());
    }
}
