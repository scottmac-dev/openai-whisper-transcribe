package com.comp3011.assignment1.responses;

/**
 * Token usage block returned by gpt-4o-mini-transcribe.
 * 
  "usage": {
    "type": "tokens",
    "input_tokens": 14,
    "input_token_details": {
      "text_tokens": 0,
      "audio_tokens": 14
    },
    "output_tokens": 42,
    "total_tokens": 165
 */
public record OpenAiUsage(
        String type,
        long input_tokens,
        OpenAiInputTokenDetails input_token_details,
        long output_tokens,
        long total_tokens
        
) {}

