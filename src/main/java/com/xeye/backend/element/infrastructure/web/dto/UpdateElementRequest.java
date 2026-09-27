package com.xeye.backend.element.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** Todos los campos son opcionales; solo se aplican los no nulos. */
public record UpdateElementRequest(
        @Size(max = 1000) String text,
        @Size(max = 8000) String params,
        @Size(max = 4000) String description) {
}
