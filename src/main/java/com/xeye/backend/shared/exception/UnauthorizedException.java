package com.xeye.backend.shared.exception;

/** Autenticación fallida (credenciales o token inválidos) -> 401. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
