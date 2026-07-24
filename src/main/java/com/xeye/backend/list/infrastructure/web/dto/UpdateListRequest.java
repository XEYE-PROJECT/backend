package com.xeye.backend.list.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/** Todos los campos son opcionales; solo se aplican los no nulos. */
public record UpdateListRequest(
        @Size(max = 100) String name,
        String description,
        @JsonProperty("public") Boolean isPublic) {
}
