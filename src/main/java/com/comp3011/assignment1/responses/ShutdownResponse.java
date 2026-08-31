package com.comp3011.assignment1.responses;

/**
 * Confirmation that a graceful shutdown request was accepted.
 *
 * @param message human-readable shutdown acknowledgement
 */
public record ShutdownResponse(
        String message
) {}
