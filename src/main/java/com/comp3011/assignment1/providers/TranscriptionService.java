package com.comp3011.assignment1.providers;

import com.comp3011.assignment1.responses.TranscriptionResult;

/**
 * Interface between the transcription endpoint and the provider performing STT.
 *
 * Enables DI for the controller, making the concrete implementation interchangeable between
 * OpenAI, a test stub, or another provider if needed.
 *
 * Takes raw bytes + filename instead of {@code MultipartFile} to keep servlet types out of the
 * service layer. Implementations can then be exercised with no web context at all, leaving it
 * open to other audio input formats or alternate routes to the frontend.
 */
public interface TranscriptionService {

    /** Largest audio payload the service will accept. Matches OpenAI's 25 MB upload limit. */
    long MAX_FILE_SIZE = 25L * 1024 * 1024;

    /**
     * Transcribes the uploaded audio bytes.
     *
     * @param audio    raw audio bytes
     * @param filename original filename; the provider infers the container format from its
     *                 extension, so it is not cosmetic
     */
    TranscriptionResult transcribe(byte[] audio, String filename);
}
