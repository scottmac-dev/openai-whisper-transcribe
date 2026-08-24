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

import com.comp3011.assignment1.responses.OpenAi4oResponse;

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
	
	// Static API endpoint and model choice, no other models or providers supported
    private static final String TRANSCRIBE_BASE_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String MODEL = "gpt-4o-transcribe";
    
    // Size limit for OpenAI audio file upload (25 MB).
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    
    // Default from application.properties when $OPENAI_API_KEY is unset
    private static final String STUB_KEY = "stub-key";
        
    // Springboot inbuilt REST API client
    private final RestClient restClient;
    
    // True when running without a real key — transcribe() returns canned data
    private final boolean stubbed;

    // Global token metrics
    private final TokenCounterProvider tokenCounter;

    // Extract API key on instantiation
    public OpenAiProvider(@Value("${openai.api-key}") String apiKey, TokenCounterProvider tokenCounter) {

    	this.tokenCounter = tokenCounter;
    	
    	// TODO: remove
    	this.stubbed = STUB_KEY.equals(apiKey);
    	if (stubbed) {
    		System.out.println("No OPENAI_API_KEY set — serving stubbed transcriptions.");
    	}
    	
    	// Create REST client using transcription API endpoint + api key in auth header
        this.restClient = RestClient.builder()
                .baseUrl(TRANSCRIBE_BASE_URL)
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
    public OpenAi4oResponse transcribe(MultipartFile audio) throws IOException {
    	
    	// TODO: remove
    	if (stubbed) {
    		return countTokens(OpenAi4oResponse.stub());
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
        body.add("model", MODEL);
        body.add("response_format", "verbose_json");
        
        // POST to endpoint, extract return type into response class
        return countTokens(restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OpenAi4oResponse.class));
    }

    // Feed the response's usage block into the global token counter
    private OpenAi4oResponse countTokens(OpenAi4oResponse res) {
        if (res != null && res.usage() != null) {
            tokenCounter.addInputTokens(res.usage().input_tokens());
            tokenCounter.addOutputTokens(res.usage().output_tokens());
        }
        return res;
    }


}
