package com.mugloar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the bot plays. All of it is config so two strategies can be benchmarked against each other
 * without a rebuild.
 *
 * @param name                     {@code expected-value} or {@code reward-per-risk}
 * @param urgencyWeight            how hard to favour ads that are about to expire
 * @param healingThresholdLives    buy a potion at or below this many lives
 * @param upgradeGoldReserve       gold held back after an upgrade when the shop has no potion
 * @param maxIdleTurns
 *        how many turns a run may give up waiting for a survivable board. 0 disables waiting.
 */
@ConfigurationProperties(prefix = "mugloar.strategy")
public record StrategyProperties(
        String name,
        double urgencyWeight,
        int healingThresholdLives,
        int upgradeGoldReserve,
        int maxIdleTurns) {
}
