package com.mugloar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param games       how many games the headless runner plays
 * @param concurrency games in flight at once; Mugloar answers 429 if you push this too hard
 * @param targetScore the bar the task sets, reported as "share of runs clearing it"
 */
@ConfigurationProperties(prefix = "mugloar.benchmark")
public record BenchmarkProperties(int games, int concurrency, int targetScore) {
}
