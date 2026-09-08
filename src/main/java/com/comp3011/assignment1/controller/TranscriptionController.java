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

import com.comp3011.assignment1.services.TranscriptionService;
import com.comp3011.assignment1.responses.ErrorResponse;
import com.comp3011.assignment1.responses.TranscriptionResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Audio upload and speech-to-text transcription.
 */
@RestController
@RequestMapping("/api/v1")
public class TranscriptionController {

    // Injected as the interface so a test can use a stub in its place without the real service.
    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    /**
     * POST /api/v1/transcribe
     *
     * Takes the recording as multipart field "audio" and returns the transcript with its
     * token usage. consumes= makes the media type part of the mapping, so a non-multipart
     * body is rejected as 415 before this method runs.
     *
     * 200 transcribed, 400 no audio, 413 too large, 415 wrong media type,
     * 502 provider error, 504 provider timeout, 500 unexpected server error.
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

        // getBytes() unwraps the servlet type here so the service layer only sees bytes.
        // ApiExceptionHandler renders the 502, 504 and 500 cases in the documented schema.
        TranscriptionResult res =
                transcriptionService.transcribe(audio.getBytes(), audio.getOriginalFilename());
        return ResponseEntity.ok(res);
    }
}
