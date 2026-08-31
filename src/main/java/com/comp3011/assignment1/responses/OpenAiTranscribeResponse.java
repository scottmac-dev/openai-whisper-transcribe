package com.comp3011.assignment1.responses;

/**
 * Maps the OpenAI gpt-4o-mini-transcribe verbose_json response.
 *
 * Example response shape:
 * {
	  "text": "Imagine the wildest idea that you've ever had...",
	  "usage": {
	    "type": "tokens",
	    "input_tokens": 14,
	    "input_token_details": {
	      "text_tokens": 0,
	      "audio_tokens": 14
	    },
	    "output_tokens": 45,
	    "total_tokens": 59
	  }
	}
 */
public record OpenAiTranscribeResponse(
        String text,
        OpenAiUsage usage
) {}
