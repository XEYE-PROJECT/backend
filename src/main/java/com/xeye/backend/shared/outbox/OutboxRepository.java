package com.xeye.backend.shared.outbox;

import java.time.Instant;
import java.util.List;

/** Persistencia del outbox. Las escrituras corren en la transacción del llamante. */
public interface OutboxRepository {

    void append(String type, String payload);

    /** Eventos pendientes cuya hora de disponibilidad ya pasó, los más antiguos primero. */
    List<OutboxEvent> findDue(Instant now, int limit);

    void delete(Long id);

    /** Reprograma un evento fallido; el relé decide el backoff. */
    void reschedule(Long id, int attempts, Instant availableAt, String error);

    /** Deja de intentar: el evento queda con estado {@code failed} para inspección. */
    void markFailed(Long id, int attempts, String error);

    long countPending();
}
