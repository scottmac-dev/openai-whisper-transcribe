package com.comp3011.assignment1.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.comp3011.assignment1.providers.UptimeProvider;
import com.comp3011.assignment1.responses.UptimeResponse;
import com.comp3011.assignment1.support.ErrorContract;

/**
 * Regression tests for the admin endpoints.
 *
 *
 * Confirming correct response shape and status code for
 * uptime 200/500 and shutdown 202/409/405.
 *
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    private static final String UPTIME = "/api/v1/admin/uptime";
    private static final String SHUTDOWN = "/api/v1/admin/shutdown";

    @Autowired
    MockMvc mvc;

    // UptimeProvider is mocked so the shutdown branch can be executed without
    // the test context actually being closed.
    @MockitoBean
    UptimeProvider uptimeProvider;

    @Test
    @DisplayName("GET uptime returns 200 with timestamps and the elapsed seconds")
    void uptimeOk() throws Exception {

        given(uptimeProvider.uptimeResponse()).willReturn(
                new UptimeResponse("2026-07-14T01:15:30Z", "2026-07-14T03:45:30.500Z", 9000.5));

        mvc.perform(get(UPTIME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utcServerStart").value("2026-07-14T01:15:30Z"))
                .andExpect(jsonPath("$.utcNow").value("2026-07-14T03:45:30.500Z"))
                .andExpect(jsonPath("$.serverUptimeSeconds").value(9000.5));
    }

    @Test
    @DisplayName("GET uptime returns the 500 error body when the provider fails")
    void uptimeServerError() throws Exception {

        given(uptimeProvider.uptimeResponse()).willThrow(new IllegalStateException("clock gone"));

        ErrorContract.assertErrorBody(mvc.perform(get(UPTIME)),
                HttpStatus.INTERNAL_SERVER_ERROR, UPTIME);
    }

    @Test
    @DisplayName("POST shutdown returns 202 when this caller starts the shutdown")
    void shutdownAccepted() throws Exception {

        given(uptimeProvider.requestShutdown()).willReturn(true);

        mvc.perform(post(SHUTDOWN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Graceful shutdown requested."));
    }

    @Test
    @DisplayName("POST shutdown returns the 409 error body when one is already running")
    void shutdownConflict() throws Exception {
        given(uptimeProvider.requestShutdown()).willReturn(false);

        ErrorContract.assertErrorBody(mvc.perform(post(SHUTDOWN)), HttpStatus.CONFLICT, SHUTDOWN);
    }

    @Test
    @DisplayName("GET shutdown returns the 405 error body")
    void shutdownWrongMethod() throws Exception {
        ErrorContract.assertErrorBody(mvc.perform(get(SHUTDOWN)),
                HttpStatus.METHOD_NOT_ALLOWED, SHUTDOWN);
    }
}
