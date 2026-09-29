package com.xeye.backend.shared.web;

import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.exception.PayloadTooLargeException;
import com.xeye.backend.shared.exception.ServiceUnavailableException;
import com.xeye.backend.shared.exception.TooManyRequestsException;
import com.xeye.backend.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapeo excepción → estado + {@code code} del {@code GlobalExceptionHandler} (sin servidor; el
 * recorrido HTTP completo lo cubre {@code ErrorHandlingIT}). Lo importante: ningún detalle
 * interno sale en un 500 y todo error lleva un código de máquina.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void domainExceptionsMapToTheirStatusAndCode() {
        assertMapped(handler.handleNotFound(new NotFoundException("x")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertMapped(handler.handleConflict(new ConflictException("x")), HttpStatus.CONFLICT, "CONFLICT");
        assertMapped(handler.handleBadRequest(new BadRequestException("x")), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertMapped(handler.handleUnauthorized(new UnauthorizedException("x")), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertMapped(handler.handleForbidden(new ForbiddenException("x")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertMapped(handler.handlePayloadTooLarge(new PayloadTooLargeException("x")), HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_TOO_LARGE");
        assertMapped(handler.handleServiceUnavailable(new ServiceUnavailableException("x")),
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }

    @Test
    void tooManyRequestsCarriesRetryAfter() {
        ResponseEntity<ApiError> response = handler.handleTooManyRequests(new TooManyRequestsException("slow down", "RATE_LIMITED", 30));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst("Retry-After"));
        assertNotNull(response.getBody().code());
    }

    @Test
    void domainIllegalArgumentIsA400NotA500() {
        assertMapped(handler.handleIllegalArgument(new IllegalArgumentException("Element text must not be blank")),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertEquals("Element text must not be blank",
                handler.handleIllegalArgument(new IllegalArgumentException("Element text must not be blank"))
                        .getBody().message());
    }

    @Test
    void concurrencyAndIntegrityAre409() {
        assertMapped(handler.handleOptimisticLock(new OptimisticLockingFailureException("v")), HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION");
        assertMapped(handler.handleDataIntegrity(new DataIntegrityViolationException("dup")), HttpStatus.CONFLICT,
                "DATA_CONFLICT");
    }

    @Test
    void unexpectedExceptionsHideTheirDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));
        assertMapped(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertEquals("Unexpected error", response.getBody().message());
        assertNull(response.getBody().details());
    }

    private static void assertMapped(ResponseEntity<ApiError> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertEquals(status.value(), response.getBody().status());
        assertEquals(code, response.getBody().code());
        assertNotNull(response.getBody().timestamp());
    }
}
