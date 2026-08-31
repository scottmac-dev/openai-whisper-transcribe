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
 * Controller for admin system information
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
	
    private final UptimeProvider uptimeProvider;

    public AdminController(UptimeProvider up) {
    	this.uptimeProvider = up;
    }
	
    /* 
     * Returns the UTC timestamp at which the server started, the current UTC
       timestamp when the response is generated, and the server uptime in
       seconds. 
       
       The uptime is computed as the difference between the current UTC timestamp and the UTC server start timestamp.
       
       Responses:
       	200 Server uptime was retrieved successfully.
       	500 An unexpected server error occurred.
       
       Endpoint: api/v1/admin/uptime
     *  */
    @GetMapping("/uptime")
    public ResponseEntity<?> uptime() {
    	// ApiExceptionHandler renders the 500 case in the documented schema.
    	UptimeResponse res = uptimeProvider.uptimeResponse();
    	return ResponseEntity.ok(res);
    }
    

    /*
     * Requests a graceful shutdown of the server. A successful response means
       the shutdown request has been accepted and the server has begun, or will
       shortly begin, its graceful shutdown sequence.
       
       Existing in-flight work
       should be allowed to complete according to the server's configured
       graceful shutdown policy.
       
       Responses:
       	202 Graceful shutdown requested.
       	409 Graceful shutdown is already in progress. 
       	500 An unexpected server error occurred.
       
       Endpoint: /api/v1/admin/shutdown
     *  */
    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown(HttpServletRequest req) {
    	
    	// 409 path for duplicate in progress attempt. 
    	// Stays in the controller rather than being mapped automatically as it is a
    	// edge case condition which should be explicit.
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
