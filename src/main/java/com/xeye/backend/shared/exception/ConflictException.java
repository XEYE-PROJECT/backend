package com.xeye.backend.shared.exception;

/** -> 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, String code) {
        super(message, code);
    }
}
