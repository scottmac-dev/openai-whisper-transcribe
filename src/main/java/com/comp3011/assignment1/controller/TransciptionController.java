package com.comp3011.assignment1.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.comp3011.assignment1.providers.TranscriptionService;
import com.comp3011.assignment1.responses.ErrorResponse;
import com.comp3011.assignment1.responses.TranscriptionResult;

import jakarta.servlet.http.HttpServletRequest;


/**
 * Controller for handling STT transcription via OpenAI API
 */
@RestController
@RequestMapping("/api/v1/")
public class TransciptionController {
	
    // Injected as the interface so a test can use a stub in its place without the real service.
    private final TranscriptionService transcriptionService;
    
    public TransciptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }
    
    /**
     * Submits an audio file for transcription, returning verbose JSON response
     * 
     * Expects audio file payload attached as audio parameter
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio,
            HttpServletRequest req) throws IOException {
        
    	// No file payload
    	if (audio.isEmpty()) {
            return ErrorResponse.entity(HttpStatus.BAD_REQUEST,
            		"No audio file was supplied.", req.getRequestURI());
        }
    	
    	// File too large
        if (audio.getSize() > TranscriptionService.MAX_FILE_SIZE) {
            return ErrorResponse.entity(HttpStatus.CONTENT_TOO_LARGE,
            		"Audio file exceeds the 25 MB upload limit.", req.getRequestURI());
        }
        
        // Extract bytes with getBytes() so provider only operates on bytes format
    	// ApiExceptionHandler renders failure cases for 504, 502 and 500 case in the documented schema.
        TranscriptionResult res =
        		transcriptionService.transcribe(audio.getBytes(), audio.getOriginalFilename());
        return ResponseEntity.ok(res);
    }
    
}
