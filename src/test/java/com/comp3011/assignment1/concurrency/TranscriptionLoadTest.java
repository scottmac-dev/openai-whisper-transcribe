package com.comp3011.assignment1.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.comp3011.assignment1.support.StubTranscriptionConfig;
import com.comp3011.assignment1.support.StubTranscriptionService;

/**
 * The API must serve more than 200 simultaneous blocking uploads without delay or failure.
 *
 * Real HTTP requests on localhost since the requirement is about simultaneous HTTP
 * requests and MockMvc never opens a socket.
 *
 * The stub is given a 2 second latency to stand in for a real transcription, which is what
 * keeps all 250 requests blocked at once.
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StubTranscriptionConfig.class)
class TranscriptionLoadTest {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionLoadTest.class);

    private static final int REQUESTS = 250;                           // Greater than 200 requirement
    private static final Duration STT_LATENCY = Duration.ofSeconds(2); // Works for 2 seconds to simulate latency
    private static final int AUDIO_BYTES = 50 * 1024;                  // UX testing shows 20 sec recording ~50 KiB

    @LocalServerPort
    int port;

    @Autowired
    StubTranscriptionService stt;

    @AfterEach
    void resetStub() {
        stt.reset();
    }

    @Test
    @DisplayName("250 simultaneous uploads all succeed, with more than 200 in flight at once")
    void handlesMoreThanTwoHundredSimultaneousUploads() throws Exception {

        stt.setLatency(STT_LATENCY);
        byte[] audio = sampleAudio();
        RestClient client = loadGenerator();

        // ready: every worker exists and is parked. startGate: they all go together.
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Attempt> attempts = new ArrayList<>(REQUESTS);

        // each request assigned a lightweight virtual thread
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {

            // store pending return of the 250 requests
            List<Future<Attempt>> pending = IntStream.range(0, REQUESTS)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        startGate.await();
                        ResponseEntity<String> res = postAudio(client, audio);
                        return new Attempt(res.getStatusCode().value(), res.getBody());
                    }))
                    .toList();

            assertThat(ready.await(30, TimeUnit.SECONDS))
                    .as("all %d workers parked on the start gate", REQUESTS)
                    .isTrue();
            startGate.countDown();

            // A request that threw instead of completing fails the test here.
            for (Future<Attempt> future : pending) {
                attempts.add(future.get(60, TimeUnit.SECONDS));
            }
        }


        // Logged before the assertions so the figures reach the build output on a failing run.
        log.info("load test: requests={} maxConcurrent={}",
                REQUESTS, stt.getMaxConcurrent());

        // Correctness must not degrade under load, all attempts return 200 status code
        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.status()).isEqualTo(200);
            assertThat(attempt.body()).contains(StubTranscriptionService.DEFAULT_TEXT);
        });

        // The requirement itself, measured rather than inferred from timing. Pins at 200 if
        // virtual threads are disabled, since that is Tomcat's platform-thread default.
        assertThat(stt.getMaxConcurrent())
                .as("requests inside the controller simultaneously")
                .isGreaterThan(200);
    }

    /** Attempt meta data */
    private record Attempt(int status, String body) {
    }

    /** Mock REST client using SimpleClientHttpRequestFactory which opens a connection per exchange */
    private RestClient loadGenerator() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // budgets larger that actual implementation, will force bad response from server if exceeding
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /** POST /api/v1/transcribe, shaped exactly as the browser client sends it. */
    private ResponseEntity<String> postAudio(RestClient client, byte[] audio) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "recording.webm";
            }
        });
        return client.post().uri("/api/v1/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                .retrieve().onStatus(s -> true, (rq, rs) -> {
                }).toEntity(String.class);
    }

    /**
     * Mocked audio bytes, the stub doesn't actually decode so as long as the payload size
     * resembles a real recording it will simulate the expected req/res pattern.
     */
    private static byte[] sampleAudio() {
        byte[] bytes = new byte[AUDIO_BYTES];
        new Random(20250908L).nextBytes(bytes); // fixed seed, so every run sends identical bytes
        return bytes;
    }
}
