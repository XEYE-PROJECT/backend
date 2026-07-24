package com.xeye.backend.search.infrastructure.web.dto;

/** Confirmación de un lote de logs de búsqueda: cuántas entradas se persistieron. */
public record IngestAck(boolean success, int accepted) {
}
