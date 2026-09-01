package com.comp3011.assignment1.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.comp3011.assignment1.providers.TokenCounterProvider;
import com.comp3011.assignment1.responses.GlobalStatsResponse;

/**
 * TokenCounterProvider is the only mutable shared state on the transcription path 
 * Every concurrent STT call writes to it and /api/v1/global/stats reads it.
 *
 * Expected result: exact totals, and every one of the reader thread's snapshots respects the
 * fixed 1:2 ratio the writers add in. 
 * 
 * Assurance: /api/v1/global/stats cannot under-report or
 * hand a client a half-applied update, however many transcriptions land at once.
 */
class TokenCounterConcurrencyTest {

    private static final int WRITERS = 200;
    private static final int ADDS_PER_WRITER = 500;

    private final TokenCounterProvider counter = new TokenCounterProvider();

    private volatile boolean writing = true;

    // Written only by the reader thread, read only after joining it.
    private boolean detectedRace;
    private long snapshots;

    @Test
    @DisplayName("200 threads x 500 adds lands exactly, and no reader ever sees a torn pair")
    void countsExactlyAndNeverTears() throws Exception {

        Thread reader = Thread.ofPlatform().daemon().start(this::pollForTornReads);
        
        // Without the gate, early threads finish before late ones start and the counter is not contended.
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < WRITERS; i++) {
                pool.submit(() -> {
                    startGate.await();
                    for (int n = 0; n < ADDS_PER_WRITER; n++) {
                        counter.add(1, 2);
                    }
                    return null;
                });
            }
            startGate.countDown();
        } // closing the executor waits for every writer

        writing = false;
        reader.join();

        long adds = (long) WRITERS * ADDS_PER_WRITER;
        GlobalStatsResponse stats = counter.getTokenStats();

        assertThat(stats.inputTokens()).as("lost input updates").isEqualTo(adds);	// must equal adds
        assertThat(stats.outputTokens()).as("lost output updates").isEqualTo(adds * 2);	// must be double inputs due to 1:2 ratio
        assertThat(detectedRace).as("stats read mid-update").isFalse();	// no race conditions detected
        assertThat(snapshots).as("reader sampled the counter").isPositive();	// should have performed snapshots (non 0)
    }

    /** Every add moves the pair 1:2 so any snapshot where output != input * 2 was a race condition. */
    private void pollForTornReads() {
        while (writing) {
            GlobalStatsResponse s = counter.getTokenStats();
            snapshots++;
            if (s.outputTokens() != s.inputTokens() * 2) {
            	// race condition identified as output is not double input at this snapshot 
                detectedRace = true;
            }
        }
    }
}
