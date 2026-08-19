package com.comp3011.assignment1.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import com.comp3011.assignment1.providers.OpenAiProvider;
import com.comp3011.assignment1.responses.OpenAiResponse;



/**
 * Controller for handling STT transcription via OpenAI API
 */
@RestController
@RequestMapping("/api/v1/")
public class TransciptionController {
	
    private final OpenAiProvider provider;	// API provider
    
    public TransciptionController(OpenAiProvider provider) {
        this.provider = provider;
    }
    
    /**
     * Submits an audio file for transcription, returning verbose JSON response
     * 
     * Expects audio file payload attached as audio parameter
     */
    @PostMapping("/transcribe")
    public ResponseEntity<OpenAiResponse> transcribe(@RequestParam("audio") MultipartFile audio) {
        
    	// No file payload
    	if (audio.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
    	
    	// File too large
        if (audio.getSize() > OpenAiProvider.MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        }
        
        // Offload to provider to handle transcription, handle error cases
        try {
        	OpenAiResponse res = provider.transcribe(audio);
        	return ResponseEntity.ok(res);
        } catch (RestClientResponseException  e) {
        	// Upstream failure
        	return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (IOException  e) {
        	// Internal failure
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
}
