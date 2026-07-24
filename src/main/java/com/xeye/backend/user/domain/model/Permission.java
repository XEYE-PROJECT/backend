package com.xeye.backend.user.domain.model;

import java.util.Locale;

/** Nivel de permiso; se persiste como cadena en minúsculas ("user" / "admin"). */
public enum Permission {

    USER,
    ADMIN;

    public static Permission fromString(String value) {
        if (value == null) {
            return USER;
        }
        return "admin".equals(value.trim().toLowerCase(Locale.ROOT)) ? ADMIN : USER;
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
