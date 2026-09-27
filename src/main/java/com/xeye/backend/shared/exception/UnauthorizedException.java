package com.xeye.backend.shared.exception;

/** -> 401. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, String code) {
        super(message, code);
    }
}
