package com.xeye.backend.shared.web;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de petición para correlacionar logs entre Caddy, este backend y el buscador.
 * Viaja en la cabecera {@code X-Request-Id}: se acepta el que pone el proxy (si es sano) o se
 * genera uno; {@link RequestIdFilter} lo deja en el MDC ({@link #MDC_KEY}) para que todas las
 * líneas de log de la petición lo lleven (patrón de consola y logs estructurados) y lo devuelve
 * en la respuesta. Las llamadas salientes al buscador lo propagan ({@link #current()}).
 */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SANE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private RequestId() {
    }

    /** El id del proxy solo si es corto y sin caracteres que rompan un log; si no, {@code null}. */
    public static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return SANE.matcher(trimmed).matches() ? trimmed : null;
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Id de la petición en curso (o del evento del outbox que se está entregando); {@code null} fuera de contexto. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
