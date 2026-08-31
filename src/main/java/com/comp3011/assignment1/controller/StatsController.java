package com.comp3011.assignment1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comp3011.assignment1.providers.TokenCounterProvider;
import com.comp3011.assignment1.responses.GlobalStatsResponse;

/**
 * Controller for global statistics
 * 
 */
@RestController
@RequestMapping("/api/v1/global")
public class StatsController {
	
	TokenCounterProvider tokenCounter;
	
	public StatsController(TokenCounterProvider tcp) {
		this.tokenCounter = tcp;
	}
	
    /* 
     * Returns cumulative input and output token usage for the speech-to-text
       Cloud service since the current UTC server start. Counters reset when
      the server process restarts.
       
       
       Responses:
       	200 Global token usage statistics were retrieved successfully.
       	500 An unexpected server error occurred.
       
       Endpoint: /api/v1/global/stats
     *  */
    @GetMapping("/stats")
    public ResponseEntity<?> globalStats() {
    	// ApiExceptionHandler renders the 500 case in the documented schema.
    	GlobalStatsResponse res = tokenCounter.getTokenStats();
    	return ResponseEntity.ok(res);
    }

}
