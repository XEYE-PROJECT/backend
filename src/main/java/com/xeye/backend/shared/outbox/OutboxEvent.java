package com.xeye.backend.shared.outbox;

import java.time.Instant;

/** Una fila de {@code outbox_events}: un evento de dominio pendiente de entregar. */
public record OutboxEvent(Long id, String type, String payload, int attempts, Instant availableAt,
                          String lastError, Instant createdAt) {
}
