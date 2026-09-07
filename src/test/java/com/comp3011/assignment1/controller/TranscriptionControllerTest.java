package com.comp3011.assignment1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.comp3011.assignment1.providers.TranscriptionService;
import com.comp3011.assignment1.responses.TokenUsage;
import com.comp3011.assignment1.responses.TranscriptionResult;
import com.comp3011.assignment1.support.ErrorContract;

/**
 * Regression tests for the transcription endpoint.
 * 
 * Confirming expected response shape and status for 
 * all possible cases 200, 400, 413, 415, 502, 504, 404
 *
 */
@WebMvcTest(TranscriptionController.class)
class TranscriptionControllerTest {

    private static final String TRANSCRIBE = "/api/v1/transcribe";

    @Autowired
    MockMvc mvc;

    // Service is mocked, so no audio is sent anywhere and no credential is needed.
    @MockitoBean
    TranscriptionService transcriptionService;

    private static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "clip.webm", "audio/webm", "fake audio".getBytes());
    }

    @Test
    @DisplayName("POST transcribe returns 200 with the transcript and its token usage")
    void transcribeOk() throws Exception {
        given(transcriptionService.transcribe(any(), anyString())).willReturn(
                new TranscriptionResult("hello world", new TokenUsage(14, 14, 0, 45, 59)));

        mvc.perform(multipart(TRANSCRIBE).file(audio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("hello world"))
                .andExpect(jsonPath("$.usage.inputTokens").value(14))
                .andExpect(jsonPath("$.usage.audioTokens").value(14))
                .andExpect(jsonPath("$.usage.textTokens").value(0))
                .andExpect(jsonPath("$.usage.outputTokens").value(45))
                .andExpect(jsonPath("$.usage.totalTokens").value(59));
    }

    @Test
    @DisplayName("POST transcribe returns 200 with null usage when the provider reports none")
    void transcribeWithoutUsage() throws Exception {

        given(transcriptionService.transcribe(any(), anyString()))
                .willReturn(new TranscriptionResult("hello world", null));

        mvc.perform(multipart(TRANSCRIBE).file(audio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("hello world"))
                .andExpect(jsonPath("$.usage").doesNotExist());
    }

    @Test
    @DisplayName("POST transcribe returns the 400 error body when the audio part is empty")
    void emptyAudioPart() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("audio", "clip.webm", "audio/webm", new byte[0]);

        ErrorContract.assertErrorBody(mvc.perform(multipart(TRANSCRIBE).file(empty)),
                HttpStatus.BAD_REQUEST, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST transcribe returns the 400 error body when the audio part is missing")
    void missingAudioPart() throws Exception {
        MockMultipartFile wrongName =
                new MockMultipartFile("file", "clip.webm", "audio/webm", "fake audio".getBytes());

        ErrorContract.assertErrorBody(mvc.perform(multipart(TRANSCRIBE).file(wrongName)),
                HttpStatus.BAD_REQUEST, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST transcribe returns the 413 error body when the audio exceeds 25 MB")
    void audioTooLarge() throws Exception {

        MockMultipartFile oversize =
                new MockMultipartFile("audio", "big.webm", "audio/webm", "fake audio".getBytes()) {
                    @Override
                    public long getSize() {
                        return TranscriptionService.MAX_FILE_SIZE + 1;
                    }
                };

        ErrorContract.assertErrorBody(mvc.perform(multipart(TRANSCRIBE).file(oversize)),
                HttpStatus.CONTENT_TOO_LARGE, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST transcribe returns the 415 error body for a non-multipart body")
    void wrongMediaType() throws Exception {
        ErrorContract.assertErrorBody(
                mvc.perform(post(TRANSCRIBE).contentType(MediaType.APPLICATION_JSON).content("{}")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST transcribe returns the 502 error body when the provider errors")
    void providerUnavailable() throws Exception {
        given(transcriptionService.transcribe(any(), anyString())).willThrow(
                new RestClientResponseException("upstream 500", 500, "Server Error", null, null, null));

        ErrorContract.assertErrorBody(mvc.perform(multipart(TRANSCRIBE).file(audio())),
                HttpStatus.BAD_GATEWAY, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST transcribe returns the 504 error body when the provider times out")
    void providerTimeout() throws Exception {
        given(transcriptionService.transcribe(any(), anyString()))
                .willThrow(new ResourceAccessException("read timed out"));

        ErrorContract.assertErrorBody(mvc.perform(multipart(TRANSCRIBE).file(audio())),
                HttpStatus.GATEWAY_TIMEOUT, TRANSCRIBE);
    }

    @Test
    @DisplayName("POST to an unmapped path returns the 404 error body")
    void unknownPath() throws Exception {
        ErrorContract.assertErrorBody(mvc.perform(post("/api/v1/nope")),
                HttpStatus.NOT_FOUND, "/api/v1/nope");
    }
}
