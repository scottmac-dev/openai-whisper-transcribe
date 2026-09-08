package com.comp3011.assignment1.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Supplies {@link StubTranscriptionService} in place of the real OpenAI implementation.
 */
@TestConfiguration
public class StubTranscriptionConfig {

    @Bean
    @Primary
    public StubTranscriptionService stubTranscriptionService() {
        return new StubTranscriptionService();
    }
}
