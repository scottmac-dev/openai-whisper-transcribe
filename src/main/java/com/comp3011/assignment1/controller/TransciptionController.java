package com.comp3011.assignment1.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import com.comp3011.assignment1.providers.OpenAiProvider;
import com.comp3011.assignment1.responses.ErrorResponse;
import com.comp3011.assignment1.responses.OpenAiTranscribeResponse;

import jakarta.servlet.http.HttpServletRequest;



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
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio, HttpServletRequest req) {
        
    	// No file payload
    	if (audio.isEmpty()) {
            return ErrorResponse.entity(HttpStatus.BAD_REQUEST,
            		"No audio file was supplied.", req.getRequestURI());
        }
    	
    	// File too large
        if (audio.getSize() > OpenAiProvider.MAX_FILE_SIZE) {
            return ErrorResponse.entity(HttpStatus.CONTENT_TOO_LARGE,
            		"Audio file exceeds the 25 MB upload limit.", req.getRequestURI());
        }
        
        // Offload to provider to handle transcription, handle error cases
        try {
        	OpenAiTranscribeResponse res = provider.transcribe(audio);
        	return ResponseEntity.ok(res);
        } catch (ResourceAccessException e) {
        	// Upstream never answered inside the timeout budget.
        	// Or an unreachable host, which fails the same way.
        	return ErrorResponse.entity(HttpStatus.GATEWAY_TIMEOUT,
        			"Transcription provider did not respond in time.", req.getRequestURI());
        } catch (RestClientResponseException  e) {
        	// Upstream failure
        	return ErrorResponse.entity(HttpStatus.BAD_GATEWAY,
        			"Transcription provider is unavailable.", req.getRequestURI());
        } catch (IOException  e) {
        	// Internal failure
            return ErrorResponse.entity(HttpStatus.INTERNAL_SERVER_ERROR,
            		"An unexpected server error occurred.", req.getRequestURI());
        }
    }
    
}
