package com.xeye.backend.element.infrastructure.web.dto;

import com.xeye.backend.element.domain.model.Element;

import java.time.Instant;

public record ElementResponse(
        Long id,
        Long listId,
        String text,
        String params,
        String description,
        String generatedDescription,
        boolean trained,
        Instant createdAt,
        Instant updatedAt) {

    public static ElementResponse from(Element element) {
        return new ElementResponse(
                element.id(),
                element.listId(),
                element.text(),
                element.params(),
                element.description(),
                element.generatedDescription(),
                element.trained(),
                element.createdAt(),
                element.updatedAt());
    }
}
