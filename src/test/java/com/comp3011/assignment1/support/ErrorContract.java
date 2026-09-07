package com.comp3011.assignment1.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared assertion for the ErrorResponse schema, used by every controller slice test.
 */
public final class ErrorContract {

    private ErrorContract() {}

    /**
     * Asserts the response is the documented error body for {@code status} on {@code path}.
     *
     * @param result the performed request
     * @param status expected HTTP status; also fixes the expected `error` reason phrase
     * @param path   expected `path` field, i.e. the request URI
     */
    public static void assertErrorBody(ResultActions result, HttpStatus status, String path)
            throws Exception {

        result.andExpect(status().is(status.value()))
                // Exactly the five fields from the schema, no more.
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(status.value()))
                .andExpect(jsonPath("$.error").value(status.getReasonPhrase()))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").value(path));
    }
}
