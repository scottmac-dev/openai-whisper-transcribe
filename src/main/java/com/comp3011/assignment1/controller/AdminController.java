package com.comp3011.assignment1.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for admin system information
 * 
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
	
	// TODO:
	//
	// /api/v1/admin/uptime
	// Returns the UTC timestamp at which the server started, the current UTC
    // timestamp when the response is generated, and the server uptime in
    // seconds. The uptime is computed as the difference between the current
    // UTC timestamp and the UTC server start timestamp.
	//
	// /api/v1/admin/shutdown
	//  Requests a graceful shutdown of the server. A successful response means
    // the shutdown request has been accepted and the server has begun, or will
    // shortly begin, its graceful shutdown sequence. Existing in-flight work
    // should be allowed to complete according to the server's configured
    // graceful shutdown policy.

}
