package com.mugloar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param games       how many games the headless runner plays
 * @param concurrency games in flight at once
 * @param startStagger
 *        minimum gap between two game starts. Mugloar sits behind Cloudflare and answers
 *        "error code: 1015" to a burst of POST /game/start, and that ban outlasts any sane retry
 *        budget - so the runner paces itself instead of retrying its way through it.
 * @param targetScore the bar the task sets, reported as "share of runs clearing it"
 */
@ConfigurationProperties(prefix = "mugloar.benchmark")
public record BenchmarkProperties(int games, int concurrency, Duration startStagger, int targetScore) {
}
