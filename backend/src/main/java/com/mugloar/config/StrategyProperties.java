package com.mugloar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strategy settings, from application.yml.
 *
 * @param name                  {@code expected-value} or {@code reward-per-risk}
 * @param urgencyWeight         weight given to ads that are about to expire
 * @param healingThresholdLives buy a potion at or below this many lives
 * @param upgradeGoldReserve    minimum gold held back after an upgrade; a potion's price at least
 * @param maxIdleTurns          turns a game may spend waiting in total; zero disables waiting
 */
@ConfigurationProperties(prefix = "mugloar.strategy")
public record StrategyProperties(
        String name,
        double urgencyWeight,
        int healingThresholdLives,
        int upgradeGoldReserve,
        int maxIdleTurns) {
}
