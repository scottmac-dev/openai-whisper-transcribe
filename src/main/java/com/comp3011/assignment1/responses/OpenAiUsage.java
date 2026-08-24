package com.comp3011.assignment1.responses;

/**
 * Token usage block returned by gpt-4o-transcribe.
 */
public record OpenAiUsage(
        String type,
        long input_tokens,
        long output_tokens,
        long total_tokens
) {}
