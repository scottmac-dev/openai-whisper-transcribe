package com.comp3011.assignment1.responses;

/*
 * Server uptime information calculated from UTC server start.
 * 
 * Expected format:
	  utcServerStart: string
	  format: date-time
	  example: "2026-07-14T01:15:30Z"
	  
	  utcNow: string
	  format: date-time
	  examples:"2026-07-14T03:45:30.500Z"
	  
	  serverUptimeSeconds: number
	  format: double
	  example: 9000.5
 *  */
public record UptimeResponse (
	String utcServerStart,
	String utcNow,
	double serverUptimeSeconds
) {}