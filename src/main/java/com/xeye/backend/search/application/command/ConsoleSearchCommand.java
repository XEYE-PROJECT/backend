package com.xeye.backend.search.application.command;

/** Búsqueda lanzada desde el playground de la consola sobre una lista propia (pública o privada). */
public record ConsoleSearchCommand(String searchTerm, int limit, boolean includeScoreBreakdown) {
}
