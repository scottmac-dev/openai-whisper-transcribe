package com.comp3011.assignment1.providers;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.comp3011.assignment1.responses.GlobalStatsResponse;

/*
 * Responsible for global token metrics tracking
 *
 * One AtomicReference to an immutable pair so that input and output count is 
 * concurrency safe and move together, showing exact snapshot of token count at the time.
 */
@Service
public class TokenCounterProvider {

    /* Immutable snapshot of both counters. */
    private record Counts(long input, long output) {}

    private final AtomicReference<Counts> counts = new AtomicReference<>(new Counts(0, 0));

    /* Both counters move in one atomic step - never add them separately. */
    public void add(long input, long output) {
        counts.updateAndGet(c -> new Counts(c.input() + input, c.output() + output));
    }

    public GlobalStatsResponse getTokenStats() {
        Counts snapshot = counts.get();
        return new GlobalStatsResponse(snapshot.input(), snapshot.output());
    }
}
