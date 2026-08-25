package com.comp3011.assignment1.responses;

/*
 * Global speech-to-text Cloud service token usage since UTC server start.
 * - inputTokens: integer
     format: int64
     minimum: 0
     
 * - outputTokens
 * 	 format: int64
     minimum: 0
 *  */
public record GlobalStatsResponse (
		long inputTokens,
		long outputTokens
) {}
