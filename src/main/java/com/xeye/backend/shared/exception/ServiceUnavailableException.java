package com.xeye.backend.shared.exception;

/** Una dependencia externa (p. ej. el search-service) no responde o no está configurada -> 503. */
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(message, "SERVICE_UNAVAILABLE");
    }
}
