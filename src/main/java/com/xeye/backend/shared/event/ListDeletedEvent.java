package com.xeye.backend.shared.event;

/** Publicado por el módulo list al borrar una lista, para que búsqueda la descarte. */
public record ListDeletedEvent(Long listId, Long userId) implements DomainEvent {

    public static final String TYPE = "LIST_DELETED";

    @Override
    public String type() {
        return TYPE;
    }
}
