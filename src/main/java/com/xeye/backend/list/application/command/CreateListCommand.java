package com.xeye.backend.list.application.command;

/** {@code llmEnrichment} false = la lista renuncia a las descripciones generadas por un LLM. */
public record CreateListCommand(String name, String description, boolean isPublic, boolean llmEnrichment) {
}
