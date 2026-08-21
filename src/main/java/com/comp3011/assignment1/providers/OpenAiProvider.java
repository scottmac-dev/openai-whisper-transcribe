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

import com.comp3011.assignment1.responses.OpenAiResponse;

/**
 * OpenAI Whisper API transcription provider.
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
    private static final String WHISPER_BASE_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String MODEL = "whisper-1";
    
    // Size limit for OpenAI audio file upload (25 MB).
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    
    // Default from application.properties when $OPENAI_API_KEY is unset
    private static final String STUB_KEY = "stub-key";
        
    // Springboot inbuilt REST API client
    private final RestClient restClient;
    
    // True when running without a real key — transcribe() returns canned data
    private final boolean stubbed;

    // Extract API key on instantiation
    public OpenAiProvider(@Value("${openai.api-key}") String apiKey) {
    	
    	// TODO: remove
    	this.stubbed = STUB_KEY.equals(apiKey);
    	if (stubbed) {
    		System.out.println("No OPENAI_API_KEY set — serving stubbed transcriptions.");
    	}
    	
    	// Create REST client using whisper API endpoint + api key in auth header
        this.restClient = RestClient.builder()
                .baseUrl(WHISPER_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
    
    /*
     * Uploads file to OpenAI whisper endpoint
     * 
     * Expected request format:
     * 	- Authorization: Bearer $API_KEY
     * 	- Content-Type: multipart/form-data
     * 	- file: /path/to/audio.webm
     * 	- model: whisper-1
     * 	- response_format: verbose_json
     * 
     * Accepted audio files: mp3, mp4, mpeg, mpga, wav, webm, m4a
     * Browser recording does webm using MediaRecorder 
     * 
     * Available reponse formats: text, json, verbose_json, srt, vtt
     * verbose_json will give the most meta data to work with
     *  */
    public OpenAiResponse transcribe(MultipartFile audio) throws IOException {
    	
    	// TODO: remove
    	if (stubbed) {
    		return OpenAiResponse.stub();
    	}
    	
    	// Convert file to byte array resource for embedding into request body
        ByteArrayResource resource = new ByteArrayResource(audio.getBytes()) {
            @Override
            public String getFilename() {
                return audio.getOriginalFilename();
            }
        };
    	
    	// Build API request to whisper endpoint
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        body.add("file", resource);
        body.add("model", MODEL);
        body.add("response_format", "verbose_json");
        
        // POST to endpoint, extract return type into response class
        return restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OpenAiResponse.class);
    }


}
