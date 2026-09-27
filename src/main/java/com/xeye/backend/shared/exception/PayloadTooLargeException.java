package com.xeye.backend.shared.exception;

/** El cuerpo de la petición supera el límite del endpoint -> 413. */
public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String message) {
        super(message, "REQUEST_TOO_LARGE");
    }
}
