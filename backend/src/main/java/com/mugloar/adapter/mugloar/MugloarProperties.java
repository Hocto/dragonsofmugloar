package com.mugloar.adapter.mugloar;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything about talking to Mugloar, from application.yml. No URL or timeout is hardcoded in
 * Java, so pointing the app at a stub or a slower network is a config change.
 */
@ConfigurationProperties(prefix = "mugloar.api")
public record MugloarProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry) {

    public record Retry(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {
    }
}
