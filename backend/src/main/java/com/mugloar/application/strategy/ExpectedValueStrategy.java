package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks ads by expected gold ({@code reward × measured success rate}) scaled by an urgency term
 * that favours ads about to expire. Two floors apply: labels that never pay are excluded outright,
 * and a survival floor rejects low-chance ads as lives run out. An empty ranking signals the
 * orchestrator to buy a potion or wait.
 */
public final class ExpectedValueStrategy implements AdSelectionStrategy {

    public static final String NAME = "expected-value";

    private final double urgencyWeight;

    public ExpectedValueStrategy(double urgencyWeight) {
        this.urgencyWeight = urgencyWeight;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<AdValuation> rank(List<Ad> ads, GameState state) {
        double floor = survivalFloor(state.lives());
        return ads.stream()
                // Hopeless labels are dropped before the reward is considered.
                .filter(ad -> ad.risk().isWorthAttempting())
                .map(this::valuate)
                .filter(valuation -> valuation.successChance() >= floor)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private AdValuation valuate(Ad ad) {
        double chance = ad.risk().successRate();
        double expectedGold = ad.reward() * chance;
        double urgency = urgency(ad.expiresIn());
        return new AdValuation(ad, chance, expectedGold, urgency, expectedGold * urgency);
    }

    /** Grows as the ad runs out of turns, so an ad expiring now outranks an equal one that can wait. */
    private double urgency(int expiresIn) {
        return 1.0 + urgencyWeight / Math.max(1, expiresIn);
    }

    /** Minimum success chance worth risking at the given lives; a failure on the last life ends the run. */
    private static double survivalFloor(int lives) {
        return switch (lives) {
            case 0, 1 -> 0.80;
            case 2 -> 0.60;
            default -> 0.0;
        };
    }
}
