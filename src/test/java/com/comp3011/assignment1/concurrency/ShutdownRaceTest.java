package com.comp3011.assignment1.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.comp3011.assignment1.controller.AdminController;
import com.comp3011.assignment1.providers.UptimeProvider;

/**
 * POST /api/v1/admin/shutdown must be won by exactly one caller with no duplication.
 *
 * Confirmed with 200 concurrent calls, expecting exactly one 202 and 199 409s.
 * 
 * The provider is handed a GenericApplicationContext, so the real SpringApplication.exit runs against that.
 */
@WebMvcTest(AdminController.class)
@Import(ShutdownRaceTest.ThrowawayContextConfig.class)
class ShutdownRaceTest {

    private static final int CALLERS = 200;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("200 concurrent shutdown requests yield exactly one 202 and 199 409s")
    void onlyOneCallerWinsTheShutdown() throws Exception {

        // Start gate ensures all requests build before first sent
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Integer>> statuses = IntStream.range(0, CALLERS)
                    .mapToObj(i -> pool.submit(() -> {
                        startGate.await();
                        return mvc.perform(post("/api/v1/admin/shutdown"))
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();

            startGate.countDown();

            int accepted = 0;
            int conflicted = 0;
            for (Future<Integer> status : statuses) {
                int code = status.get(30, TimeUnit.SECONDS);
                if (code == 202) {
                    accepted++;
                } else if (code == 409) {
                    conflicted++;
                } else {
                    throw new AssertionError("unexpected status from shutdown: " + code);
                }
            }
            
            // only 1 202 status code return
            assertThat(accepted).as("callers that started the shutdown").isEqualTo(1);
            
            // remaining get 409 response
            assertThat(conflicted).as("callers correctly rejected as duplicates")
                    .isEqualTo(CALLERS - 1);
        }
    }

    /**
     * Supplies the real UptimeProvider wired to a context that is safe to close.
     */
    @TestConfiguration
    static class ThrowawayContextConfig {

        @Bean
        UptimeProvider uptimeProvider() {
            ConfigurableApplicationContext sacrificial = new GenericApplicationContext();
            sacrificial.refresh();
            return new UptimeProvider(sacrificial);
        }
    }
}
