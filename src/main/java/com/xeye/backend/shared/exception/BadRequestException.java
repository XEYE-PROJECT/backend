package com.xeye.backend.shared.exception;

/** -> 400. */
public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, String code) {
        super(message, code);
    }
}
