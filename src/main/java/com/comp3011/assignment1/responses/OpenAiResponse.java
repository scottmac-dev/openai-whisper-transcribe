package com.comp3011.assignment1.responses;

import java.util.List;

/**
 * A single Whisper segment from the OpenAI verbose_json response.
 * 
 * Each segment carries a time window, transcribed text, token IDs, and decoding quality metrics
 * 
 */
record OpenAiSegment(
        int id,
        int seek,
        double start,
        double end,
        String text,
        int[] tokens,
        double temperature,
        double avg_logprob,
        double compression_ratio,
        double no_speech_prob
) {}


/**
 * Maps the OpenAI Whisper API verbose_json response.
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
public record OpenAiResponse(
        String task,
        String language,
        double duration,
        String text,
        List<OpenAiSegment> segments
) {}
