package com.xeye.backend.shared.exception;

/** El llamante está autenticado pero no puede tocar este recurso -> 403. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
