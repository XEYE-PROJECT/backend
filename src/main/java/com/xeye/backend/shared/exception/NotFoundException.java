package com.xeye.backend.shared.exception;

/** El recurso no existe (o no es visible para el llamante) -> 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
