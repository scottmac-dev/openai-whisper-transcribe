package com.comp3011.assignment1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comp3011.assignment1.providers.UptimeProvider;
import com.comp3011.assignment1.responses.ErrorResponse;
import com.comp3011.assignment1.responses.ShutdownResponse;
import com.comp3011.assignment1.responses.UptimeResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Admin system information and server lifecycle.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UptimeProvider uptimeProvider;

    public AdminController(UptimeProvider up) {
        this.uptimeProvider = up;
    }

    /**
     * GET /api/v1/admin/uptime
     *
     * Returns the UTC server start timestamp, the UTC timestamp of this response, and the
     * difference between them in seconds.
     *
     * 200 uptime retrieved, 500 unexpected server error.
     */
    @GetMapping("/uptime")
    public ResponseEntity<?> uptime() {
        // ApiExceptionHandler renders the 500 case in the documented schema.
        UptimeResponse res = uptimeProvider.uptimeResponse();
        return ResponseEntity.ok(res);
    }

    /**
     * POST /api/v1/admin/shutdown
     *
     * A 202 means the shutdown was accepted, not that it has finished. In-flight work is
     * allowed to complete under the configured graceful shutdown policy.
     *
     * 202 shutdown requested, 409 already in progress, 500 unexpected server error.
     */
    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown(HttpServletRequest req) {

        // Duplicate request while one is already running. Kept in the controller rather than
        // thrown, since it is an expected outcome of this endpoint rather than a failure.
        if (!uptimeProvider.requestShutdown()) {
            return ErrorResponse.entity(HttpStatus.CONFLICT,
                    "Graceful shutdown is already in progress.", req.getRequestURI());
        }

        // ApiExceptionHandler renders the 500 case in the documented schema.
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new ShutdownResponse(
                        "Graceful shutdown requested."
                ));
    }
}
