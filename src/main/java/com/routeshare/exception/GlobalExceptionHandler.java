package com.routeshare.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * GlobalExceptionHandler standardizes error responses across the REST API:
 * every error body is a JSON object with an "error" field, so clients never
 * have to guess between plain-text and JSON error shapes.
 *
 * Demonstrates:
 * - Separation of Concerns (SE Principle 2): Error mapping is centralized
 *   instead of duplicated in each controller.
 * - Robustness (Quality Attribute, Ch. 2): Uncaught exceptions surface as
 *   well-formed, non-leaking JSON responses with appropriate status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean-validation failures on @Valid bodies → 400 with BOTH a summary message
     * ("error", kept for backward compatibility) and a per-field map ("fieldErrors")
     * that the SPA uses for inline highlighting next to each input.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        java.util.Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));
        String message = fieldErrors.entrySet().stream()
                .findFirst()
                .map(en -> en.getKey() + ": " + en.getValue())
                .orElse("Validation failed.");
        return ResponseEntity.badRequest().body(Map.of("error", message, "fieldErrors", fieldErrors));
    }

    /**
     * A missing or unparseable request parameter → 400, not 500.
     *
     * The mutating endpoints require an {@code actorId}; omitting it is a client
     * mistake and must read as one.
     */
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
                       org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadParameter(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Illegal state-machine transitions and similar → 409 CONFLICT. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /**
     * Acting on someone else's record → 403 FORBIDDEN.
     *
     * Ownership is enforced by the controllers and BookingLifecycleService, which raise
     * SecurityException; mapping it once here means every guarded endpoint answers with
     * the same shape instead of leaking a 500.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    /** Referential-integrity violations → 409 with a readable message. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "The operation violates data integrity (the record is still referenced)."));
    }

    /** Unknown paths keep their natural 404 semantics. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Resource not found."));
    }

    /** Last-resort net: never leak stack traces to clients. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unhandled exception reached the API boundary", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }
}
