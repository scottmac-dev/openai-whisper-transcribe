package com.comp3011.assignment1.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.comp3011.assignment1.responses.OpenAiInputTokenDetails;
import com.comp3011.assignment1.responses.OpenAiTranscribeResponse;
import com.comp3011.assignment1.responses.OpenAiUsage;
import com.comp3011.assignment1.responses.TokenUsage;
import com.comp3011.assignment1.responses.TranscriptionResult;

/**
 * OpenAI transcription provider.
 * 
 * TranscriptionService backed by the real OpenAI /audio/transcriptions endpoint.
 *
 * Only wired in when OPENAI_API_KEY is actually present. 
 * With no key Spring picks LocalStubTranscriptionService instead.
 */
@Service
@ConditionalOnExpression("!'${openai.api-key:}'.isBlank()")
public class OpenAiTranscriptionService implements TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionService.class);

    // Springboot inbuilt REST API client
    private final RestClient restClient;

    // Transcription model, from openai.model
    private final String model;
    
    // Global token metrics
    private final TokenCounterProvider tokenCounter;

    /*
     * Endpoint, model and API key all arrive from application.properties.
     *
     * The endpoint is a property rather than a constant so a test can point the
     * upstream at a local stub without calling and paying the real OpenAI service.
     */
    public OpenAiTranscriptionService(RestClient.Builder restClientBuilder,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.api-key}") String apiKey,
            TokenCounterProvider tokenCounter) {

        this.model = model;
    	this.tokenCounter = tokenCounter;
    	
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
     * Uploads audio file bytes to OpenAI transcription endpoint
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
     * Available reponse formats: text, json, srt, vtt
     *  */
    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename) {
    	
    	// Wrap the bytes so the encoder sends them as a named file part. 
    	// Endpoint infers format off the extension, so the filename xyz.webm has to be available.
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    	
    	// Build API request to transcription endpoint
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        body.add("file", resource);
        body.add("model", model);
        body.add("response_format", "json");
        body.add("language", "en");
        
        // POST to endpoint, decode the provider's shape, then map straight out of it
        long startedAt = System.nanoTime();
        TranscriptionResult result = countTokens(toResult(restClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OpenAiTranscribeResponse.class)));

        // Basic oneline log per successful request for tracing and debugging
        // No sensitive data logged, just length, time and token count
        log.info("STT ok model={} bytes={} ms={} inputTokens={} outputTokens={}",
                model, audio.length, (System.nanoTime() - startedAt) / 1_000_000,
                result.usage() == null ? 0 : result.usage().inputTokens(),
                result.usage() == null ? 0 : result.usage().outputTokens());

        return result;
    }

    /*
     * Adapter that maps OpenAIs response JSON into internal TranscriptionResult representation
     */
    private TranscriptionResult toResult(OpenAiTranscribeResponse res) {
        if (res == null) {
            return null;
        }
        OpenAiUsage usage = res.usage();
        if (usage == null) {
            return new TranscriptionResult(res.text(), null);
        }
        OpenAiInputTokenDetails details = usage.input_token_details();
        return new TranscriptionResult(res.text(), new TokenUsage(
                usage.input_tokens(),
                details == null ? 0L : details.audio_tokens(),
                details == null ? 0L : details.text_tokens(),
                usage.output_tokens(),
                usage.total_tokens()));
    }

    // Feed the call's usage into the global token counter
    private TranscriptionResult countTokens(TranscriptionResult result) {
        if (result != null && result.usage() != null) {
            tokenCounter.add(result.usage().inputTokens(), result.usage().outputTokens());
        }
        return result;
    }


}
