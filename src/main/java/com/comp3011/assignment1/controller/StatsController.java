package com.comp3011.assignment1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comp3011.assignment1.providers.TokenCounterProvider;
import com.comp3011.assignment1.responses.GlobalStatsResponse;

/**
 * Global service statistics.
 */
@RestController
@RequestMapping("/api/v1/global")
public class StatsController {

    private final TokenCounterProvider tokenCounter;

    public StatsController(TokenCounterProvider tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * GET /api/v1/global/stats
     *
     * Cumulative input and output token usage for the STT service since server start.
     * Counters reset when the process restarts.
     *
     * 200 statistics retrieved, 500 unexpected server error.
     */
    @GetMapping("/stats")
    public ResponseEntity<GlobalStatsResponse> globalStats() {
        // ApiExceptionHandler renders the 500 case in the documented schema.
        GlobalStatsResponse res = tokenCounter.getTokenStats();
        return ResponseEntity.ok(res);
    }
}
