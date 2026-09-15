package com.mugloar.adapter.mugloar;

import com.mugloar.application.port.MugloarApiException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry with exponential backoff, for the handful of calls this app makes.
 *
 * <p>Deliberately not Spring Retry or Resilience4j. Those are the right answer when you need
 * circuit breakers, bulkheads and metrics; here it is one policy applied to six methods, and thirty
 * lines is cheaper to read than a dependency plus its configuration.
 *
 * <p>What is retried matters more than how: 429 and 5xx and transport failures are worth another
 * go, and every other 4xx is a bug in the request that will fail identically the second time.
 */
final class Backoff {

    private static final Logger log = LoggerFactory.getLogger(Backoff.class);

    private final MugloarProperties.Retry config;

    Backoff(MugloarProperties.Retry config) {
        this.config = config;
    }

    <T> T call(String operation, Supplier<T> action) {
        Duration wait = config.initialBackoff();
        MugloarApiException last = null;

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (MugloarApiException e) {
                last = e;
                if (!isWorthRetrying(e) || attempt == config.maxAttempts()) {
                    throw e;
                }
                log.warn("mugloar.retry op={} attempt={}/{} status={} backoffMs={} reason=\"{}\"",
                        operation, attempt, config.maxAttempts(), e.status(), wait.toMillis(),
                        e.getMessage());
                sleep(wait);
                wait = nextWait(wait);
            }
        }
        throw last;
    }

    private boolean isWorthRetrying(MugloarApiException e) {
        return e.isWorthRetrying();
    }

    private Duration nextWait(Duration current) {
        Duration scaled = Duration.ofMillis((long) (current.toMillis() * config.multiplier()));
        return scaled.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : scaled;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MugloarApiException("Interrupted while backing off", 0, e);
        }
    }
}
