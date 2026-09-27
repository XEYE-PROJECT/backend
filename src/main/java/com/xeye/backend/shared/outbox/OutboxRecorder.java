package com.xeye.backend.shared.outbox;

import com.xeye.backend.shared.event.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Convierte cada {@link DomainEvent} publicado en una fila del outbox <em>dentro de la
 * transacción del publicador</em> ({@code @EventListener} es síncrono): o se comitean el
 * cambio y su evento, o ninguno. Tras el commit despierta al relé para que la entrega no
 * espere al siguiente sondeo.
 */
@Component
public class OutboxRecorder {

    private final OutboxRepository outbox;
    private final OutboxRelay relay;
    private final ObjectMapper json;

    public OutboxRecorder(OutboxRepository outbox, OutboxRelay relay, ObjectMapper json) {
        this.outbox = outbox;
        this.relay = relay;
        this.json = json;
    }

    @EventListener
    public void record(DomainEvent event) {
        outbox.append(event.type(), json.writeValueAsString(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void wake(DomainEvent event) {
        relay.trigger();
    }
}
