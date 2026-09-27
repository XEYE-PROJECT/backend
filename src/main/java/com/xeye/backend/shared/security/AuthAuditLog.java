package com.xeye.backend.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registro de eventos de seguridad (logins fallidos, tokens inválidos, bloqueos…) en un logger
 * propio ({@code xeye.audit}) para poder enrutarlos/alertar aparte. Nunca escribe secretos ni
 * el email completo: se enmascara ({@code j***@example.com}).
 */
public final class AuthAuditLog {

    private static final Logger log = LoggerFactory.getLogger("xeye.audit");

    private AuthAuditLog() {
    }

    public static void info(String event, String ip, String email, String detail) {
        log.info("event={} ip={} email={} {}", event, ip, mask(email), detail == null ? "" : detail);
    }

    public static void warn(String event, String ip, String email, String detail) {
        log.warn("event={} ip={} email={} {}", event, ip, mask(email), detail == null ? "" : detail);
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email.charAt(0) + "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
