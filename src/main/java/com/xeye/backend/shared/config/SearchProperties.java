package com.xeye.backend.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Vincula {@code xeye.search.*}. Vive en {@code shared} porque dos módulos hablan con el
 * servicio de búsqueda: {@code training} (push del índice al completar) y {@code search}
 * (notificaciones + API interna). {@code internalToken} es el secreto compartido en ambas
 * direcciones del tráfico backend↔search; se valida al arrancar (nunca en blanco).
 */
@Validated
@ConfigurationProperties(prefix = "xeye.search")
public record SearchProperties(@NotBlank String provider,
                               @NotBlank String url,
                               @NotBlank String internalServiceName,
                               @NotBlank String internalToken) {
}
