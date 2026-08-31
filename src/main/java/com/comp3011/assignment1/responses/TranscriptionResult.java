package com.comp3011.assignment1.responses;

/**
 * Internal representation of result from STT.
 * 
 * Decouples internal representation from provider response format.
 *
 * @param text  the transcribed speech
 * @param usage token usage for the call, or null if the provider reported none
 */
public record TranscriptionResult(
        String text,
        TokenUsage usage
) {}
