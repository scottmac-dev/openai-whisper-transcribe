package com.comp3011.assignment1.responses;

/**
 * Maps the OpenAI gpt-4o-transcribe verbose_json response.
 *
 * Example response shape:
 * {
 *   "text": "Hello, this is a transcription.",
 *   "usage": {
 *     "type": "tokens",
 *     "input_tokens": 123,
 *     "output_tokens": 8,
 *     "total_tokens": 131
 *   }
 * }
 */
public record OpenAi4oResponse(
        String text,
        OpenAiUsage usage
) {
	/**
	 * Canned response used when no real OpenAI API key is configured
	 */
	public static OpenAi4oResponse stub() {
		return new OpenAi4oResponse(
				"This is a stubbed transcription. Set OPENAI_API_KEY to call the real API.",
				new OpenAiUsage("tokens", 123, 8, 131)
		);
	}
}
