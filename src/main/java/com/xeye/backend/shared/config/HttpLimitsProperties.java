package com.xeye.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code xeye.http.*}: topes de tamaño del cuerpo de las peticiones (bytes).
 *
 * @param maxBodyBytes        cualquier endpoint sin regla propia
 * @param importMaxBodyBytes  {@code POST /lists/{id}/elements/import}
 * @param webhookMaxBodyBytes {@code POST /webhooks/**} (embeddings en base64 de la lista entera)
 * @param internalMaxBodyBytes {@code /internal/**} (lotes de logs del buscador)
 */
@ConfigurationProperties(prefix = "xeye.http")
public record HttpLimitsProperties(long maxBodyBytes, long importMaxBodyBytes, long webhookMaxBodyBytes,
                                   long internalMaxBodyBytes) {
}
