package com.xeye.backend.shared.exception;

/** -> 403. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, String code) {
        super(message, code);
    }
}
