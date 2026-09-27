package com.xeye.backend.shared.exception;

/** Límite de intentos superado (cuenta bloqueada o rate limit) -> 429 con {@code Retry-After}. */
public class TooManyRequestsException extends DomainException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, String code, long retryAfterSeconds) {
        super(message, code);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
