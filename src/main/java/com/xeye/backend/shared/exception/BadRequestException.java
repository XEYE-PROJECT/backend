package com.xeye.backend.shared.exception;

/** Petición semánticamente inválida más allá del bean validation -> 400. */
public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }
}
