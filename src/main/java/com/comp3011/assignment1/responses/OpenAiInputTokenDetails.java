package com.comp3011.assignment1.responses;

/**
 * Nested input token breakdown from the OpenAI response.
 *
 * @param text_tokens  the text portion of input_tokens
 * @param audio_tokens the audio portion of input_tokens
 */
public record OpenAiInputTokenDetails(
        long text_tokens,
        long audio_tokens
) {}
