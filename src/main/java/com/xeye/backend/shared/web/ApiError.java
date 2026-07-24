package com.xeye.backend.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Cuerpo JSON de error uniforme para toda excepción manejada. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> details,
        Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, Instant.now());
    }

    public static ApiError of(int status, String error, String message, Map<String, String> details) {
        return new ApiError(status, error, message, details, Instant.now());
    }
}
