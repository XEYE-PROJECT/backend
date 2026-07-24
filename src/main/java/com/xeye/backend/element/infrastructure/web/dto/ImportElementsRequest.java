package com.xeye.backend.element.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Importación masiva. {@code params} acepta cualquier valor JSON (objeto, array, cadena…);
 * los no-cadena se serializan a su texto JSON antes de guardarse, siguiendo el contrato de
 * cadena opaca de {@link CreateElementRequest}.
 */
public record ImportElementsRequest(
        @NotEmpty @Size(max = 1000) List<@Valid Item> elements) {

    public record Item(
            @NotBlank String text,
            Object params,
            String description) {
    }
}
