package com.comp3011.assignment1.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.comp3011.assignment1.responses.TokenUsage;
import com.comp3011.assignment1.responses.TranscriptionResult;

/**
 * Canned TranscriptionService wired in when no OPENAI_API_KEY is present, so the app still
 * starts and the frontend can be worked on without a key or a network connection.
 */
@Service
@ConditionalOnExpression("'${openai.api-key:}'.isBlank()")
public class LocalStubTranscriptionService implements TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LocalStubTranscriptionService.class);

    // Says so in the transcript itself, so a stubbed response can never be mistaken for a
    // real one in the UI.
    private static final String STUB_TEXT =
            "This is a stubbed transcription. Set OPENAI_API_KEY to call the real API.";

    private static final TokenUsage STUB_USAGE = new TokenUsage(1200, 1184, 16, 480, 1680);

    private final TokenCounterProvider tokenCounter;
    
    // Log to console when starting without valid key in environment.
    public LocalStubTranscriptionService(TokenCounterProvider tokenCounter) {
        this.tokenCounter = tokenCounter;
        log.warn("No OPENAI_API_KEY set - serving stubbed transcriptions. "
                + "Set the environment variable to call the real service.");
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename) {
        log.debug("Stubbed transcription for {} ({} bytes)", filename, audio == null ? 0 : audio.length);
        tokenCounter.add(STUB_USAGE.inputTokens(), STUB_USAGE.outputTokens());
        return new TranscriptionResult(STUB_TEXT, STUB_USAGE);
    }
}
