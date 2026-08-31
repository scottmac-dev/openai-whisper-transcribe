package com.comp3011.assignment1.providers;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.comp3011.assignment1.responses.GlobalStatsResponse;

/**
 * Global token metrics tracking.
 *
 * One AtomicReference to an immutable pair, so the input and output counts are concurrency
 * safe and move together. Every read is therefore an exact snapshot of one moment, rather
 * than two counters read a fraction apart.
 */
@Service
public class TokenCounterProvider {

    /** Immutable snapshot of both counters. */
    private record Counts(long input, long output) {}

    private final AtomicReference<Counts> counts = new AtomicReference<>(new Counts(0, 0));

    /** Both counters move in one atomic step. */
    public void add(long input, long output) {
        counts.updateAndGet(c -> new Counts(c.input() + input, c.output() + output));
    }

    public GlobalStatsResponse getTokenStats() {
        Counts snapshot = counts.get();
        return new GlobalStatsResponse(snapshot.input(), snapshot.output());
    }
}
