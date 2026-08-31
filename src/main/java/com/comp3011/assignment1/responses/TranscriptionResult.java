package com.comp3011.assignment1.responses;

/**
 * Internal representation of an STT result, decoupling us from any provider's response shape.
 *
 * @param text  the transcribed speech
 * @param usage token usage for the call, or null if the provider reported none
 */
public record TranscriptionResult(
        String text,
        TokenUsage usage
) {}
