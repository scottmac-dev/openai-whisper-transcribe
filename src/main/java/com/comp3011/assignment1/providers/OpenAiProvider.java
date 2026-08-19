package com.comp3011.assignment1.providers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

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
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    
    // Springboot inbuilt REST API client
    private final RestClient restClient;

    // Extract API key on instantiation
    public OpenAiProvider(@Value("${openai.api-key}") String apiKey) {
    	
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
    	
    	// Build API request to whisper endpoint
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", audio.getResource())
            .filename(audio.getOriginalFilename());
        body.part("model", MODEL);
        body.part("response_format", "verbose_json");
        
        // POST to endpoint, extract return type into response class
        return restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(OpenAiResponse.class);
    }


}
