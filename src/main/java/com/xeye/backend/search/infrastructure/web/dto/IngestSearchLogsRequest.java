package com.xeye.backend.search.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lote de logs de búsqueda reportado por el microservicio de búsqueda. Se valida la forma
 * (tamaños y obligatorios) antes de persistir nada: un lote malformado responde 400 y el
 * buscador lo descarta tras sus reintentos; una entrada cuya lista o clave ya no existe se
 * omite individualmente en el servicio.
 */
public record IngestSearchLogsRequest(@NotNull @Size(max = 500) List<@Valid Entry> logs) {

    public record Entry(
            @NotNull Long userId,
            Long apiKeyId,
            Long listId,
            @NotBlank @Size(max = 100) String listName,
            @NotBlank @Size(max = 20) String endpoint,
            @NotNull @Size(max = 2000) String searchTerm,
            @PositiveOrZero Integer totalResults,
            @PositiveOrZero Integer durationMs,
            @Size(max = 255) String session,
            @Size(max = 500) Map<String, Double> results,
            @NotNull Instant searchedAt) {
    }
}
