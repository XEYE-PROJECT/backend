package com.xeye.backend.shared.exception;

/**
 * Base de las violaciones de reglas de negocio. Los subtipos se mapean a códigos HTTP en
 * {@link com.xeye.backend.shared.web.GlobalExceptionHandler}; el dominio nunca referencia HTTP.
 * {@code code} es un identificador legible por máquina opcional (p. ej. {@code EMAIL_NOT_VERIFIED})
 * que el cliente puede usar para decidir qué mostrar, independientemente del mensaje.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String message) {
        this(message, null);
    }

    protected DomainException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
