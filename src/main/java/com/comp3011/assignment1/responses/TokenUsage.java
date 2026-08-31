package com.comp3011.assignment1.responses;

/**
 * Token usage for a single transcription.
 *
 * Internal flattened response format no provider nested JSON.
 * 
 * Provider responses are mapped onto internal representation to decouple 
 * backend from specific provider API.
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
