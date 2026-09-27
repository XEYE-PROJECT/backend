package com.xeye.backend.shared.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code xeye.outbox.*}.
 *
 * @param pollMs      cada cuánto revisa el relé si hay eventos vencidos (el commit de un evento
 *                    además lo despierta al instante)
 * @param batchSize   eventos por pasada
 * @param maxAttempts intentos antes de dejar el evento como {@code failed} (log ERROR → Sentry)
 */
@ConfigurationProperties(prefix = "xeye.outbox")
public record OutboxProperties(long pollMs, int batchSize, int maxAttempts) {
}
