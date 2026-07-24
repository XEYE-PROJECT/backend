package com.xeye.backend.element.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code params} es una cadena opaca (JSON o texto plano) que se guarda tal cual. */
public record CreateElementRequest(
        @NotBlank String text,
        String params,
        String description) {
}
