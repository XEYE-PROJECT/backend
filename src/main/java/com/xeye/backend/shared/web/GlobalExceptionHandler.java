package com.xeye.backend.shared.web;

import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.DomainException;
import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.exception.PayloadTooLargeException;
import com.xeye.backend.shared.exception.ServiceUnavailableException;
import com.xeye.backend.shared.exception.TooManyRequestsException;
import com.xeye.backend.shared.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce a JSON {@link ApiError} (siempre con {@code code} de máquina) tanto las excepciones
 * de dominio como las del framework: JSON mal formado, tipos de parámetro, ruta inexistente,
 * método no soportado, validación, concurrencia… Nada de eso debe acabar en un 500. El 500
 * queda para lo realmente inesperado, con stack trace en el log (y en Sentry) y sin detalles
 * al cliente. Los controladores nunca capturan excepciones: suben hasta aquí.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Dominio ----

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex, "NOT_FOUND");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex, "CONFLICT");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex, "BAD_REQUEST");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex, "UNAUTHORIZED");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex, "FORBIDDEN");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiError> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex, "REQUEST_TOO_LARGE");
    }

    /** Dependencia externa caída o no configurada (p. ej. el search-service para el playground). */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.warn("Service unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex, "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfterSeconds())))
                .body(error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(),
                        ex.code() == null ? "RATE_LIMITED" : ex.code()));
    }

    /**
     * Invariantes del dominio rotas por la entrada (p. ej. "Element text must not be blank",
     * "Unknown training status"): es culpa de la petición, no del servidor.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,
                ex.getMessage() == null ? "Invalid request" : ex.getMessage(), "INVALID_ARGUMENT"));
    }

    // ---- Seguridad ----

    /** {@code @PreAuthorize} denegado (p. ej. un usuario normal en /admin/**). */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleAuthorizationDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(HttpStatus.FORBIDDEN, "Insufficient permissions", "FORBIDDEN"));
    }

    // ---- Validación y forma de la petición ----

    /** Fallos de bean validation en bodies con @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> details.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> details.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return validationFailed(details);
    }

    /** Bean validation sobre parámetros de método (@RequestParam/@PathVariable con constraints). */
    @ExceptionHandler({HandlerMethodValidationException.class, MethodValidationException.class})
    public ResponseEntity<ApiError> handleMethodValidation(Exception ex) {
        return validationFailed(Map.of("request", ex.getMessage() == null ? "invalid parameters" : ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> details.putIfAbsent(
                v.getPropertyPath() == null ? "request" : v.getPropertyPath().toString(), v.getMessage()));
        return validationFailed(details);
    }

    /** JSON mal formado o de tipo incompatible (p. ej. una cadena donde va un número). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof PayloadTooLargeException tooLarge) {
                return handlePayloadTooLarge(tooLarge);
            }
            cause = cause.getCause();
        }
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,
                "Malformed or unreadable request body", "MALFORMED_BODY"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName();
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' must be a valid " + expected, "INVALID_PARAMETER",
                Map.of(ex.getName(), "must be a valid " + expected)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,
                "Missing parameter '" + ex.getParameterName() + "'", "MISSING_PARAMETER",
                Map.of(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported Content-Type; send application/json", "UNSUPPORTED_MEDIA_TYPE"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(error(HttpStatus.NOT_ACCEPTABLE, "This API only produces application/json", "NOT_ACCEPTABLE"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large", "REQUEST_TOO_LARGE"));
    }

    // ---- Enrutado ----

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(HttpStatus.NOT_FOUND, "No such endpoint", "NOT_FOUND"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            response.allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return response.body(error(HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not allowed on this endpoint", "METHOD_NOT_ALLOWED"));
    }

    /** {@code ResponseStatusException} y compañía: se respeta su estado. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleErrorResponse(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            log.error("Unhandled error response", ex);
            message = "Unexpected error";
        }
        return ResponseEntity.status(status).body(error(status, message,
                status.name()));
    }

    // ---- Persistencia y concurrencia ----

    /** Dos escrituras concurrentes sobre la misma fila (bloqueo optimista): que el cliente recargue y repita. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT,
                "The resource was modified by another request; reload and retry", "CONCURRENT_MODIFICATION"));
    }

    /** Restricción de BD violada (unicidad, FK): casi siempre una carrera o un id que ya no existe. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT,
                "The request conflicts with existing data", "DATA_CONFLICT"));
    }

    // ---- Último recurso ----

    /** Loguea el stack trace y oculta los detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "INTERNAL_ERROR"));
    }

    private static ResponseEntity<ApiError> validationFailed(Map<String, String> details) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "Validation failed",
                "VALIDATION_FAILED", details));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, DomainException ex, String defaultCode) {
        return ResponseEntity.status(status)
                .body(error(status, ex.getMessage(), ex.code() == null ? defaultCode : ex.code()));
    }

    private static ApiError error(HttpStatus status, String message, String code) {
        return ApiError.of(status.value(), status.getReasonPhrase(), message, code);
    }

    private static ApiError error(HttpStatus status, String message, String code, Map<String, String> details) {
        return ApiError.of(status.value(), status.getReasonPhrase(), message, code, details);
    }
}
