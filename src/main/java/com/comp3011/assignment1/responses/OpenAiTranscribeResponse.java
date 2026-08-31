package com.comp3011.assignment1.responses;

/**
 * Wire shape of the OpenAI gpt-4o-mini-transcribe response.
 *
 * Decoded from upstream JSON then mapped onto TranscriptionResult, so these snake_case
 * component names never reach our own API. Example:
 *
 * <pre>
 * {
 *   "text": "Imagine the wildest idea that you've ever had...",
 *   "usage": {
 *     "type": "tokens",
 *     "input_tokens": 14,
 *     "input_token_details": { "text_tokens": 0, "audio_tokens": 14 },
 *     "output_tokens": 45,
 *     "total_tokens": 59
 *   }
 * }
 * </pre>
 */
public record OpenAiTranscribeResponse(
        String text,
        OpenAiUsage usage
) {}
