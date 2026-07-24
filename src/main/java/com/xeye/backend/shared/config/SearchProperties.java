package com.xeye.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula {@code xeye.search.*}. Vive en {@code shared} porque dos módulos hablan con el
 * servicio de búsqueda: {@code training} (push del índice al completar) y {@code search}
 * (notificaciones + API interna). {@code internalToken} es el secreto compartido en ambas
 * direcciones del tráfico backend↔search.
 */
@ConfigurationProperties(prefix = "xeye.search")
public record SearchProperties(String provider, String url, String internalServiceName, String internalToken) {
}
