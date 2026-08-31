package com.comp3011.assignment1.responses;

/**
 * Server uptime information calculated from UTC server start.
 *
 * @param utcServerStart      process start time, RFC 3339, e.g. "2026-07-14T01:15:30Z"
 * @param utcNow              time this response was generated, e.g. "2026-07-14T03:45:30.500Z"
 * @param serverUptimeSeconds seconds between utcServerStart and utcNow, e.g. 9000.5
 */
public record UptimeResponse(
        String utcServerStart,
        String utcNow,
        double serverUptimeSeconds
) {}
