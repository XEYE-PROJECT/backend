package com.xeye.backend.list.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code llmEnrichment} (por defecto true): false = sin descripciones generadas por un LLM (opt-out). */
public record CreateListRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @JsonProperty("public") Boolean isPublic,
        Boolean llmEnrichment) {

    public boolean publicOrDefault() {
        return isPublic != null && isPublic;
    }

    public boolean llmEnrichmentOrDefault() {
        return llmEnrichment == null || llmEnrichment;
    }
}
