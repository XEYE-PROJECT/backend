package com.xeye.backend.shared.outbox;

import com.xeye.backend.shared.web.RequestId;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entrega los eventos del outbox en orden, en un único hilo: sondeo periódico
 * ({@code xeye.outbox.poll-ms}) más un despertar inmediato tras cada commit que grabó uno.
 * Cada evento se entrega fuera de toda transacción de BD (son llamadas HTTP). Un fallo
 * reprograma el evento con backoff exponencial (2, 4, 8… s, tope 10 min) y, agotados los
 * intentos, lo deja como {@code failed} con un log ERROR (que Sentry recoge). Los eventos con
 * el mismo tipo y payload dentro de una pasada se entregan una sola vez (p. ej. diez
 * invalidaciones seguidas de la misma lista).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final OutboxRepository outbox;
    private final Map<String, OutboxHandler> handlers = new HashMap<>();
    private final OutboxProperties properties;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "outbox-relay");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    public OutboxRelay(OutboxRepository outbox, List<OutboxHandler> handlers, OutboxProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
        for (OutboxHandler handler : handlers) {
            for (String type : handler.types()) {
                OutboxHandler previous = this.handlers.put(type, handler);
                if (previous != null) {
                    throw new IllegalStateException("Two outbox handlers for event type " + type);
                }
            }
        }
    }

    /** Pide una pasada cuanto antes (sin encolar más de una). */
    public void trigger() {
        if (scheduled.compareAndSet(false, true)) {
            worker.execute(this::drainSafely);
        }
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "${xeye.outbox.poll-ms:5000}")
    void poll() {
        trigger();
    }

    private void drainSafely() {
        scheduled.set(false);
        try {
            drain();
        } catch (Exception ex) {
            log.error("Outbox relay pass failed", ex);
        }
    }

    /** Una pasada: entrega todo lo vencido, por lotes, hasta que no quede nada. Visible para tests. */
    void drain() {
        while (true) {
            List<OutboxEvent> due = outbox.findDue(Instant.now(), Math.max(1, properties.batchSize()));
            if (due.isEmpty()) {
                return;
            }
            // Coalescer duplicados exactos: se entrega el último y se borra el resto.
            Map<String, OutboxEvent> latest = new HashMap<>();
            for (OutboxEvent event : due) {
                OutboxEvent previous = latest.put(event.type() + "|" + event.payload(), event);
                if (previous != null) {
                    outbox.delete(previous.id());
                }
            }
            for (OutboxEvent event : due) {
                if (latest.get(event.type() + "|" + event.payload()) == event) {
                    deliver(event);
                }
            }
            if (due.size() < properties.batchSize()) {
                return;
            }
        }
    }

    private void deliver(OutboxEvent event) {
        // Correlación: los logs (y la cabecera X-Request-Id hacia el buscador) de esta entrega
        // llevan el id del evento, ya que aquí no hay petición HTTP entrante.
        MDC.put(RequestId.MDC_KEY, "outbox-" + event.id());
        try {
            deliverEvent(event);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }

    private void deliverEvent(OutboxEvent event) {
        OutboxHandler handler = handlers.get(event.type());
        if (handler == null) {
            log.error("No outbox handler for event type {} (id {}); marking failed", event.type(), event.id());
            outbox.markFailed(event.id(), event.attempts() + 1, "no handler for type " + event.type());
            return;
        }
        try {
            handler.handle(event);
            outbox.delete(event.id());
        } catch (Exception ex) {
            int attempts = event.attempts() + 1;
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            if (attempts >= Math.max(1, properties.maxAttempts())) {
                log.error("Outbox event {} ({}) failed permanently after {} attempts: {}",
                        event.id(), event.type(), attempts, error);
                outbox.markFailed(event.id(), attempts, error);
                return;
            }
            Instant retryAt = Instant.now().plus(backoff(attempts));
            log.warn("Outbox event {} ({}) failed (attempt {}): {}; retrying at {}",
                    event.id(), event.type(), attempts, error, retryAt);
            outbox.reschedule(event.id(), attempts, retryAt, error);
        }
    }

    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(attempts, 20);
        Duration wait = Duration.ofSeconds(seconds);
        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
        try {
            worker.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
