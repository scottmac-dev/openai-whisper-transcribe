package com.comp3011.assignment1.responses;

/**
 * A single Whisper segment from the OpenAI verbose_json response.
 * 
 * Each segment carries a time window, transcribed text, token IDs, and decoding quality metrics
 * 
 */
public record OpenAiSegment(
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