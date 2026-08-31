package com.comp3011.assignment1.responses;

/**
 * Token usage block returned by gpt-4o-mini-transcribe.
 *
 * Component names match OpenAI's JSON so Jackson binds them without annotations. Mapped onto
 * TokenUsage before anything else sees it.
 *
 * @param type                always "tokens"; kept only because upstream sends it
 * @param input_tokens        total input tokens
 * @param input_token_details the audio/text breakdown of input_tokens
 * @param output_tokens       tokens produced in the transcript
 * @param total_tokens        input plus output
 */
public record OpenAiUsage(
        String type,
        long input_tokens,
        OpenAiInputTokenDetails input_token_details,
        long output_tokens,
        long total_tokens
) {}
