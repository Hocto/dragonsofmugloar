package com.mugloar.web;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins CORS origins for the development server; unused when the UI is proxied
 * @param autoTurnDelay  pause between automatic turns
 * @param maxRuns        cap on runs held in memory before finished ones are evicted
 * @param streamTimeout  how long an idle SSE connection is held open
 */
@ConfigurationProperties(prefix = "mugloar.web")
public record WebProperties(
        List<String> allowedOrigins,
        Duration autoTurnDelay,
        int maxRuns,
        Duration streamTimeout) {
}
