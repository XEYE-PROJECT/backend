package com.xeye.backend.shared.exception;

/**
 * Base de las violaciones de reglas de negocio. Los subtipos se mapean a códigos HTTP en
 * {@link com.xeye.backend.shared.web.GlobalExceptionHandler}; el dominio nunca referencia HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
