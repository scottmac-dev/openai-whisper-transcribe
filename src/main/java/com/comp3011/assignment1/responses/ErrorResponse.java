package com.comp3011.assignment1.responses;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Standard JSON error body returned when an operation fails, per the YAML spec.
 *
 * The spec sets additionalProperties: false, so these five fields are exactly the shape -
 * nothing may be added to it.
 *
 * @param timestamp UTC time the error was generated, RFC 3339, e.g. "2026-07-14T03:45:30Z"
 * @param status    HTTP status code, 400-599
 * @param error     HTTP reason phrase, e.g. "Internal Server Error"
 * @param message   human-readable description of the failure
 * @param path      request path that produced the error, e.g. "/api/v1/admin/uptime"
 */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
    /** Builds the body, taking the reason phrase straight from the status. */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
    }

    /** Same, wrapped ready to return from a controller. */
    public static ResponseEntity<ErrorResponse> entity(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(of(status, message, path));
    }
}
