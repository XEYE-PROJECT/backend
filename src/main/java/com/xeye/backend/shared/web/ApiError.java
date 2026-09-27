package com.xeye.backend.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo JSON de error uniforme para toda excepción manejada. {@code code} (opcional) es un
 * identificador estable para el cliente (p. ej. {@code EMAIL_NOT_VERIFIED}, {@code ACCOUNT_LOCKED}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        String code,
        Map<String, String> details,
        Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, null, Instant.now());
    }

    public static ApiError of(int status, String error, String message, String code) {
        return new ApiError(status, error, message, code, null, Instant.now());
    }

    public static ApiError of(int status, String error, String message, Map<String, String> details) {
        return new ApiError(status, error, message, null, details, Instant.now());
    }
}
