package com.comp3011.assignment1.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.comp3011.assignment1.support.LogCapture;

/**
 * The STT credential must never reach a client or the log, on any path.
 *
 * Assurance: 200, 404, 405, 415, 413 and 504 all return bodies free of the credential, and
 * nothing logged while serving them contains it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=" + ApiKeyLeakageTest.TEST_KEY,
        // Dead port: every transcription fails upstream, which is where a leak would surface.
        "openai.base-url=http://localhost:59999/dead",
        // Small enough that a 16 KB payload trips the container limit without a 26 MB file.
        "spring.servlet.multipart.max-file-size=8KB",
        "spring.servlet.multipart.max-request-size=9KB" })
class ApiKeyLeakageTest {

    static final String TEST_KEY = "sk-test-must-never-be-logged";

    @LocalServerPort
    int port;

    @Autowired
    RestClient.Builder builder;

    @Test
    @DisplayName("no response body on any path contains the API key")
    void keyNeverReachesAClient() {
        try (LogCapture logs = new LogCapture()) {
            RestClient client = builder.clone().baseUrl("http://localhost:" + port).build();

            assertNoKey(get(client, "/api/v1/admin/uptime"), 200);
            assertNoKey(get(client, "/api/v1/global/stats"), 200);
            assertNoKey(get(client, "/api/v1/nope"), 404);
            assertNoKey(get(client, "/api/v1/admin/shutdown"), 405);
            assertNoKey(postJson(client), 415);
            assertNoKey(postAudio(client, new byte[16 * 1024]), 413);
            assertNoKey(postAudio(client, new byte[512]), 504);

            // Nothing logged while serving any of the above may carry the credential
            assertThat(logs.anyMessageContains(TEST_KEY))
                    .as("API key found in log output")
                    .isFalse();
            assertThat(logs.anyMessageContains("Bearer"))
                    .as("Authorization header found in log output")
                    .isFalse();
        }
    }
    
    /** Helper validating key string not logged in responses */
    private void assertNoKey(ResponseEntity<String> response, int expectedStatus) {
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody() == null ? "" : response.getBody())
                .as("API key leaked in a %s response body", expectedStatus)
                .doesNotContain(TEST_KEY);
    }
    
    /** Test GET request */
    private ResponseEntity<String> get(RestClient client, String path) {
        return client.get().uri(path).retrieve().onStatus(s -> true, (rq, rs) -> {
        }).toEntity(String.class);
    }
    
    /** Test POST request with JSON payload */
    private ResponseEntity<String> postJson(RestClient client) {
        return client.post().uri("/api/v1/transcribe")
                .contentType(MediaType.APPLICATION_JSON).body("{}")
                .retrieve().onStatus(s -> true, (rq, rs) -> {
                }).toEntity(String.class);
    }

    /** Test POST request with JSON audio file payload */
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
}
