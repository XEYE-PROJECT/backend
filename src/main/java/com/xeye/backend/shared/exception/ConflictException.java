package com.xeye.backend.shared.exception;

/** La petición choca con el estado actual (p. ej. email duplicado) -> 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
