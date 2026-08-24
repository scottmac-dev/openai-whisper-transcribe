package com.comp3011.assignment1.responses;

import java.util.List;


/**
 * Maps the OpenAI Whisper API verbose_json response.
 *
 * Kept as a fallback — the app currently uses OpenAi4oResponse.
 *
 * Contains metadata and an ordered list of OpenAiSegment objects.
 * 
 * Example response shape:
 * {
	  "text": "Hello, this is a test recording...",
	  "language": "english",
	  "segments": [
	    {
	      "id": 0,
	      "seek": 0,
	      "start": 0.0,
	      "end": 3.32,
	      "text": " Hello, this is a test recording...",
	      "tokens": [50364, 2425, 374, 279, 1659, 12846, ...],
	      "temperature": 0.0,
	      "avg_logprob": -0.23700379133224487,
	      "compression_ratio": 0.6046511627906977,
	      "no_speech_prob": 0.007896838661193848
	    }
	  ],
	  ...
	}
 *
 */
public record OpenAiWhisperResponse(
        String task,
        String language,
        double duration,
        String text,
        List<OpenAiSegment> segments
) {
	/**
	 * Canned response used when no real OpenAI API key is configured
	 */
	public static OpenAiWhisperResponse stub() {
		return new OpenAiWhisperResponse(
				"transcribe",
				"english",
				7.4,
				" This is a stubbed transcription. Set OPENAI_API_KEY to call the real Whisper API.",
				List.of(
						new OpenAiSegment(0, 0, 0.0, 3.6,
								" This is a stubbed transcription.",
								new int[] { 50364, 639, 307, 257 },
								0.0, -0.24, 0.61, 0.008),
						new OpenAiSegment(1, 0, 3.6, 7.4,
								" Set OPENAI_API_KEY to call the real Whisper API.",
								new int[] { 8928, 46, 22940, 40, 62 },
								0.0, -0.19, 0.72, 0.004)
				)
		);
	}
}
