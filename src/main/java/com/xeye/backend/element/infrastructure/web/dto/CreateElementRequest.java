package com.xeye.backend.element.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code params} es una cadena opaca (JSON o texto plano) que se guarda tal cual. */
public record CreateElementRequest(
        @NotBlank @Size(max = 1000) String text,
        @Size(max = 8000) String params,
        @Size(max = 4000) String description) {
}
