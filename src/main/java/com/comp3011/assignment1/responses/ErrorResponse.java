package com.comp3011.assignment1.responses;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/*
 * Standard JSON error response returned when an operation fails.
 * 
 * Expected format:
	  timestamp: string
	  format: date-time
	  example: "2026-07-14T03:45:30Z"
	  
	  status: integer
	  format: int32 (400-599)
	  example: 500
	  
	  error: string
	  example: "Internal Server Error"
	  
	  message: string
	  example: "An unexpected server error occurred."
	  
	  path: string
	  example: "/api/v1/admin/uptime"
 *  */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
	/* Builds the body, taking the reason phrase straight from the status */
	public static ErrorResponse of(HttpStatus status, String message, String path) {
		return new ErrorResponse(
				Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
				status.value(),
				status.getReasonPhrase(),
				message,
				path
		);
	}
	
	/* Same, wrapped ready to return from a controller */
	public static ResponseEntity<ErrorResponse> entity(HttpStatus status, String message, String path) {
		return ResponseEntity.status(status).body(of(status, message, path));
	}
}
