package com.mugloar.web;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins  only needed for the Vite dev server; in Docker the UI is same-origin
 * @param autoTurnDelay   pause between automatic turns so a human can follow the stream
 * @param maxRuns         cap on runs held in memory before the oldest finished ones are dropped
 * @param streamTimeout   how long an idle SSE connection is held open
 */
@ConfigurationProperties(prefix = "mugloar.web")
public record WebProperties(
        List<String> allowedOrigins,
        Duration autoTurnDelay,
        int maxRuns,
        Duration streamTimeout) {
}
