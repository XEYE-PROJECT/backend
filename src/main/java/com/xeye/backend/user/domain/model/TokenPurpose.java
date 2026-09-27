package com.xeye.backend.user.domain.model;

import java.time.Duration;
import java.util.Locale;

/** Para qué sirve un token de un solo uso enviado por email, y cuánto dura. */
public enum TokenPurpose {

    VERIFY_EMAIL(Duration.ofHours(24)),
    RESET_PASSWORD(Duration.ofHours(1)),
    CHANGE_EMAIL(Duration.ofHours(1));

    private final Duration validity;

    TokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TokenPurpose fromString(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
