package com.comp3011.assignment1.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.comp3011.assignment1.providers.TokenCounterProvider;
import com.comp3011.assignment1.responses.GlobalStatsResponse;
import com.comp3011.assignment1.support.ErrorContract;

/**
 * Regression tests for the global stats endpoint: 200 and 500.
 */
@WebMvcTest(StatsController.class)
class StatsControllerTest {

    private static final String STATS = "/api/v1/global/stats";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TokenCounterProvider tokenCounter;

    @Test
    @DisplayName("GET stats returns 200 with both cumulative counters")
    void statsOk() throws Exception {
        given(tokenCounter.getTokenStats()).willReturn(new GlobalStatsResponse(1234L, 567L));

        mvc.perform(get(STATS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTokens").value(1234))
                .andExpect(jsonPath("$.outputTokens").value(567));
    }

    @Test
    @DisplayName("GET stats returns the 500 error body when the counter fails")
    void statsServerError() throws Exception {
        given(tokenCounter.getTokenStats()).willThrow(new IllegalStateException("counter gone"));

        ErrorContract.assertErrorBody(mvc.perform(get(STATS)),
                HttpStatus.INTERNAL_SERVER_ERROR, STATS);
    }
}
