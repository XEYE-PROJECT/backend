package com.xeye.backend.shared.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.function.ToDoubleFunction;

/**
 * Gauges Prometheus del outbox: {@code xeye_outbox_pending} (eventos por entregar; crece cuando
 * el buscador no responde) y {@code xeye_outbox_failed} (agotaron los intentos: hay que mirar
 * la tabla {@code outbox_events}). Se leen de la BD en cada scrape.
 */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, OutboxRepository outbox) {
        gauge(registry, "xeye.outbox.pending", "Outbox events waiting for delivery", outbox, OutboxRepository::countPending);
        gauge(registry, "xeye.outbox.failed", "Outbox events that exhausted their attempts", outbox, OutboxRepository::countFailed);
    }

    private static void gauge(MeterRegistry registry, String name, String description, OutboxRepository outbox,
                              ToDoubleFunction<OutboxRepository> reader) {
        Gauge.builder(name, outbox, o -> {
                    try {
                        return reader.applyAsDouble(o);
                    } catch (RuntimeException ex) {
                        return Double.NaN;
                    }
                })
                .description(description)
                .register(registry);
    }
}
