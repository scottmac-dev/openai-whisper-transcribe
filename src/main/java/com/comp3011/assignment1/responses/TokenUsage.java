package com.comp3011.assignment1.responses;

/**
 * Token usage for a single transcription.
 *
 * Flattened internal format with no provider nesting: OpenAI reports the audio/text split
 * inside input_token_details, which carries no meaning for us, so both are hoisted alongside
 * the total they break down.
 *
 * @param inputTokens  total tokens consumed by the audio submitted
 * @param audioTokens  the audio portion of inputTokens
 * @param textTokens   the text portion of inputTokens (prompt, if any)
 * @param outputTokens tokens produced in the transcript
 * @param totalTokens  inputTokens + outputTokens, as reported by the provider
 */
public record TokenUsage(
        long inputTokens,
        long audioTokens,
        long textTokens,
        long outputTokens,
        long totalTokens
) {}
