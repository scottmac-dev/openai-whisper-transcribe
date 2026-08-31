package com.comp3011.assignment1.providers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import com.comp3011.assignment1.responses.UptimeResponse;

/**
 * Server lifecycle state: when the process started, how long it has been up, and the
 * one-way transition into shutdown.
 */
@Service
public class UptimeProvider {

    private final ConfigurableApplicationContext context;
    private final Instant serverStartTimeUTC = Instant.now();

    // Prevent duplicate shutdown requests
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public UptimeProvider(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /** UTC timestamp at which the server process started, encoded as RFC 3339. */
    private String startTime() {
        return serverStartTimeUTC.toString();
    }

    /** Current UTC timestamp at response generation time, encoded as RFC 3339. */
    private String now() {
        return Instant.now().toString();
    }

    /** Floating point number of seconds between utcServerStart and utcNow. */
    private double uptime() {
        Duration uptimeDuration = Duration.between(serverStartTimeUTC, Instant.now());
        return uptimeDuration.getSeconds() + (uptimeDuration.getNano() / 1_000_000_000.0);
    }

    /** Serialised to UptimeResponse for API return. */
    public UptimeResponse uptimeResponse() {
        return new UptimeResponse(this.startTime(), this.now(), this.uptime());
    }

    /**
     * Concurrency safe shutdown request. The CAS means only the first caller wins; every
     * later one gets false and the controller turns that into a 409.
     *
     * The exit runs off the request thread, otherwise the context would close while the
     * response was still being written.
     *
     * @return true if this call started the shutdown, false if one was already running
     */
    public boolean requestShutdown() {

        if (!shutdownRequested.compareAndSet(false, true)) {
            return false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                SpringApplication.exit(context);
            } catch (Exception e) {
                shutdownRequested.set(false);
            }
        });

        return true;
    }

}
