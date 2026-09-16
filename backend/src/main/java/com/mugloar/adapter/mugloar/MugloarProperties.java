package com.mugloar.adapter.mugloar;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the Mugloar API, from application.yml. */
@ConfigurationProperties(prefix = "mugloar.api")
public record MugloarProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry) {

    public record Retry(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {
    }
}
