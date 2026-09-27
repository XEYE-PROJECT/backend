package com.xeye.backend.shared.event;

/**
 * Evento de dominio que sale del proceso (hacia el buscador o el flujo de training). Se publica
 * con {@code ApplicationEventPublisher} dentro de la transacción del cambio; el
 * {@code OutboxRecorder} lo persiste en {@code outbox_events} en esa misma transacción y el
 * {@code OutboxRelay} lo entrega después con reintentos. {@link #type()} es la clave estable con
 * la que el relé elige el handler y deserializa el payload.
 */
public interface DomainEvent {

    String type();
}
