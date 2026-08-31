package com.comp3011.assignment1.responses;

/**
 * Global STT token usage since UTC server start.
 *
 * @param inputTokens  total input tokens consumed, int64, never negative
 * @param outputTokens total output tokens produced, int64, never negative
 */
public record GlobalStatsResponse(
        long inputTokens,
        long outputTokens
) {}
