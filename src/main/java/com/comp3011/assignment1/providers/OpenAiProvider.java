package com.comp3011.assignment1.providers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.comp3011.assignment1.responses.OpenAiResponse;

/**
 * OpenAI Whisper API transcription provider.
 *
 * Uses the /audio/transcriptions endpoint with verbose_json for rich meta data return.
 *
 * Requires $OPENAI_API_KEY environment variable provided at runtime. 
 */
@Component
@ConditionalOnProperty("openai.api-key")
public class OpenAiProvider {
	
	// Static API endpoint and model choice, no other models or providers supported
    private static final String WHISPER_BASE_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String MODEL = "whisper-1";
    
    // Size limit for OpenAI audio file upload (25 MB).
    protected static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    
    // Springboot inbuilt REST API client
    private final RestClient restClient;

    // Extract API key on instantiation
    public OpenAiProvider(@Value("${openai.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(WHISPER_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
    
    // TODO
    public OpenAiResponse transcribe() {}


}
