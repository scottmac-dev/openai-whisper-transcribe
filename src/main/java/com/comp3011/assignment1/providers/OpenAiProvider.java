package com.comp3011.assignment1.providers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.comp3011.assignment1.responses.OpenAiTranscribeResponse;

/**
 * OpenAI transcription provider.
 *
 * Uses the /audio/transcriptions endpoint with verbose_json for rich meta data return.
 *
 * Requires $OPENAI_API_KEY environment variable provided at runtime. Without it
 * the key falls back to STUB_KEY and transcribe() returns a canned response.
 */
@Component
@ConditionalOnProperty("openai.api-key")
public class OpenAiProvider {

    // Size limit for OpenAI audio file upload (25 MB).
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    
    // Default from application.properties when $OPENAI_API_KEY is unset
    private static final String STUB_KEY = "stub-key";
        
    // Springboot inbuilt REST API client
    private final RestClient restClient;

    // Transcription model, from openai.model
    private final String model;
    
    // True when running without a real key — transcribe() returns canned data
    private final boolean stubbed;

    // Global token metrics
    private final TokenCounterProvider tokenCounter;

    /*
     * Endpoint, model and API key all arrive from application.properties.
     *
     * The endpoint is a property rather than a constant so a test can point the
     * upstream at a local stub without calling and paying the real OpenAI service.
     */
    public OpenAiProvider(RestClient.Builder restClientBuilder,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.api-key}") String apiKey,
            TokenCounterProvider tokenCounter) {

        this.model = model;
    	this.tokenCounter = tokenCounter;
    	
    	// TODO: remove
    	this.stubbed = STUB_KEY.equals(apiKey);
    	if (stubbed) {
    		System.out.println("No OPENAI_API_KEY set — serving stubbed transcriptions.");
    	}
    	
        /*
         * Built from the injected builder rather than the static RestClient.builder().
         * The static factory bypasses Boot's auto-configuration, so the configured timeouts
         * would never reach the client
         */
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
    
    /*
     * Uploads file to OpenAI transcription endpoint
     * 
     * Expected request format:
     * 	- Authorization: Bearer $API_KEY
     * 	- Content-Type: multipart/form-data
     * 	- file: /path/to/audio.webm
     * 	- model: gpt-4o-transcribe
     * 	- response_format: verbose_json
     * 
     * Accepted audio files: mp3, mp4, mpeg, mpga, wav, webm, m4a
     * Browser recording does webm using MediaRecorder 
     * 
     * Available reponse formats: text, json, verbose_json, srt, vtt
     * verbose_json will give the most meta data to work with
     *  */
    public OpenAiTranscribeResponse transcribe(MultipartFile audio) throws IOException {
    	
    	// TODO: remove
    	if (stubbed) {
    		return countTokens(OpenAiTranscribeResponse.stub());
    	}
    	
    	// Convert file to byte array resource for embedding into request body
        ByteArrayResource resource = new ByteArrayResource(audio.getBytes()) {
            @Override
            public String getFilename() {
                return audio.getOriginalFilename();
            }
        };
    	
    	// Build API request to transcription endpoint
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        body.add("file", resource);
        body.add("model", model);
        body.add("response_format", "json");
        body.add("language", "en");
        
        // POST to endpoint, extract return type into response class
        return countTokens(restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OpenAiTranscribeResponse.class));
    }

    // Feed the response's usage block into the global token counter
    private OpenAiTranscribeResponse countTokens(OpenAiTranscribeResponse res) {
        if (res != null && res.usage() != null) {
            tokenCounter.addInputTokens(res.usage().input_tokens());
            tokenCounter.addOutputTokens(res.usage().output_tokens());
        }
        return res;
    }


}
