package com.xeye.backend.element.infrastructure.web.dto;

/** Todos los campos son opcionales; solo se aplican los no nulos. */
public record UpdateElementRequest(
        String text,
        String params,
        String description) {
}
