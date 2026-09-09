package com.mugloar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the bot plays. All of it is config so two strategies can be benchmarked against each other
 * without a rebuild.
 *
 * @param name                     {@code expected-value} or {@code reward-per-risk}
 * @param levelLift                how fast dragon level pulls success chances towards certainty
 * @param urgencyWeight            how hard to favour ads that are about to expire
 * @param healingThresholdLives    buy a potion at or below this many lives
 * @param upgradeGoldReserve       gold held back after an upgrade when the shop has no potion
 * @param investigateReputationEveryTurns
 *        0 disables it. Investigating costs a turn, which is the whole problem - see the README.
 */
@ConfigurationProperties(prefix = "mugloar.strategy")
public record StrategyProperties(
        String name,
        double levelLift,
        double urgencyWeight,
        int healingThresholdLives,
        int upgradeGoldReserve,
        int investigateReputationEveryTurns) {
}
