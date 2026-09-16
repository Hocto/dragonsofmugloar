package com.mugloar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param games        how many games the headless runner plays
 * @param concurrency  games in flight at once
 * @param startStagger minimum gap between game starts; the upstream bans bursts of game starts
 * @param targetScore  score threshold reported as "share of runs clearing it"
 */
@ConfigurationProperties(prefix = "mugloar.benchmark")
public record BenchmarkProperties(int games, int concurrency, Duration startStagger, int targetScore) {
}
