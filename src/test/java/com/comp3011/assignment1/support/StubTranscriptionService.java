package com.comp3011.assignment1.support;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.comp3011.assignment1.services.TranscriptionService;
import com.comp3011.assignment1.responses.TokenUsage;
import com.comp3011.assignment1.responses.TranscriptionResult;

/**
 * Stub {@link TranscriptionService} standing in for the real service in tests.
 */
public class StubTranscriptionService implements TranscriptionService {

    /** Stubbed transcript returned. */
    public static final String DEFAULT_TEXT = "This is a real transcription, wow!";

    private volatile TranscriptionResult response = stubbedResponse(DEFAULT_TEXT);

    // Artificial upstream latency. Zero by default unless set in unit test.
    private volatile Duration latency = Duration.ZERO;

    //  Used to simulate 502/504 responses without needing a real upstream to fail.
    private volatile RuntimeException failure;

    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicLong lastAudioSize = new AtomicLong(-1);
    private volatile String lastFilename;

    // Callers currently inside transcribe() call
    private final AtomicInteger inFlight = new AtomicInteger();
    
    // The highest concurrent count reached during unit test
    // This is what can be asserted as direct evidence of holding N concurrent requests
    private final AtomicInteger maxConcurrent = new AtomicInteger();

    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename) {
        callCount.incrementAndGet();
        lastAudioSize.set(audio == null ? -1 : audio.length);
        lastFilename = filename;

        // Raise the max count on entry
        maxConcurrent.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            Duration delay = this.latency;
            if (!delay.isZero() && !delay.isNegative()) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while stubbing transcription", e);
                }
            }

            RuntimeException toThrow = this.failure;
            if (toThrow != null) {
                throw toThrow;
            }
            return response;
        } finally {
            inFlight.decrementAndGet();
        }
    }
    
    /**
     * A stubbed result carrying token counts, so tests assert against the same schema the client has to parse.
     */
    public static TranscriptionResult stubbedResponse(String text) {
        return new TranscriptionResult(text, new TokenUsage(1200, 1184, 16, 480, 1680));
    }

    // ---- configuration helpers -------------------------------------------------

    /** Returns this exact response once set. */
    public StubTranscriptionService setResponse(TranscriptionResult response) {
        this.response = response;
        return this;
    }

    /** Returns a stubbed response carrying the given text. */
    public StubTranscriptionService setText(String text) {
        return setResponse(stubbedResponse(text));
    }

    /** Blocks for this long before answering, imitating latency. */
    public StubTranscriptionService setLatency(Duration latency) {
        this.latency = latency;
        return this;
    }

    /** Throws this exception instead of answering */
    public StubTranscriptionService failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    /** Clears configured behaviour and counters */
    public void reset() {
        this.response = stubbedResponse(DEFAULT_TEXT);
        this.latency = Duration.ZERO;
        this.failure = null;
        this.callCount.set(0);
        this.lastAudioSize.set(-1);
        this.lastFilename = null;
        this.inFlight.set(0);
        this.maxConcurrent.set(0);
    }

    // ---- assertion helpers ---------------------------------------------------------
    
    /** Total calls made */
    public int getCallCount() {
        return callCount.get();
    }

    /**
     * The most callers ever inside {@link #transcribe} at the same moment.
     */
    public int getMaxConcurrent() {
        return maxConcurrent.get();
    }

    /** Bytes received on the most recent call */
    public long getLastAudioSize() {
        return lastAudioSize.get();
    }

    public String getLastFilename() {
        return lastFilename;
    }
}
