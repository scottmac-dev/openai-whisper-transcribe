package com.comp3011.assignment1.providers;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.comp3011.assignment1.responses.GlobalStatsResponse;

/*
 * Responsible for global token metrics tracking
 *  */
@Service
public class TokenCounterProvider {
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    
    public TokenCounterProvider() {}
    
    public void addInputTokens(long input) {
        inputTokens.addAndGet(input);
    }
    
    public void addOutputTokens(long output) {
        outputTokens.addAndGet(output);
    }

    public long getInputTokens() {
        return inputTokens.get();
    }

    public long getOutputTokens() {
        return outputTokens.get();
    }

    public long getTotalTokens() {
        return inputTokens.get() + outputTokens.get();
    }
    
    public GlobalStatsResponse getTokenStats() {
    	return new GlobalStatsResponse(this.getInputTokens(), this.getOutputTokens());
    }
}
