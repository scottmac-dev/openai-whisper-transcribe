package com.comp3011.assignment1.responses;

/* Nested input token breakdown from API response */
public record OpenAiInputTokenDetails(
		long text_tokens,
		long audio_tokens
) {}
