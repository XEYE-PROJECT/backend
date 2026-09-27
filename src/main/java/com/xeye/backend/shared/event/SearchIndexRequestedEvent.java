package com.xeye.backend.shared.event;

/**
 * Un training completó o pasó a {@code in_use}: hay que empujar su índice al buscador. Se
 * entrega por el outbox, fuera de la transacción que lo produjo (la llamada HTTP nunca corre
 * dentro de una transacción de BD).
 */
public record SearchIndexRequestedEvent(Long listId, Long trainingId) implements DomainEvent {

    public static final String TYPE = "SEARCH_INDEX_REQUESTED";

    @Override
    public String type() {
        return TYPE;
    }
}
