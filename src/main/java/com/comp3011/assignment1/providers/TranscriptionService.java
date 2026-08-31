package com.comp3011.assignment1.providers;

import com.comp3011.assignment1.responses.TranscriptionResult;

/**
 * Interface between TranscriptionController endpoint and the provider performing STT
 *
 * Enables DI for controller, making the concrete implementation interchangeable between
 * OpenAI, a test stub, or leaving open for extension to other providers if needed.
 *
 * Use raw bytes + filename instead of {@code MultipartFile} to keep servlet types out of 
 * the service layer. Implementation can then be exercised with no web context at all, leaving 
 * it extensible to various audio input formats or alternate routes to web frontend. 
 */
public interface TranscriptionService {

    /**
     * Largest audio payload the service will accept. 
     * Matches OpenAI's 25 MB upload limit.
     */
    long MAX_FILE_SIZE = 25L * 1024 * 1024;

    /**
     * Transcribes the uploaded audio bytes.
     */
    TranscriptionResult transcribe(byte[] audio, String filename);
}
