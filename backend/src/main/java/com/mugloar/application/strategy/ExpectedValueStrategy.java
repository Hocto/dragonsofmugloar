package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.Comparator;
import java.util.List;

/**
 * The strategy that clears 1000.
 *
 * <p>Three ideas, in the order they matter:
 *
 * <ol>
 *   <li><b>Expected gold, not advertised gold.</b> A 200 gold "Suicide mission" is worth about four
 *       gold and a life; a 60 gold "Sure thing" is worth about forty-five. The multiplier comes
 *       from {@link com.mugloar.domain.RiskLevel}, which is measured rather than guessed.
 *   <li><b>Urgency.</b> Ads expire. Two ads worth the same expected gold are not equally valuable
 *       if one disappears next turn and the other has six turns left - the second will still be
 *       there after I have banked the first. This is the term that changed the results most.
 *   <li><b>Floors.</b> Two of them. A flat one that refuses the labels which never pay, and a
 *       survival one that gets stricter as lives run out. When the survival floor rejects the whole
 *       board, the strategy returns nothing, which is the orchestrator's cue to go buy a potion.
 * </ol>
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
                // Hopeless labels are dropped before anything else looks at the reward, so a big
                // number on a "Suicide mission" never gets the chance to be tempting.
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

    /**
     * Grows as the ad runs out of turns. An ad expiring this turn is worth taking now; one with six
     * turns left can wait, so it is discounted relative to its expected gold.
     */
    private double urgency(int expiresIn) {
        return 1.0 + urgencyWeight / Math.max(1, expiresIn);
    }

    /**
     * The minimum success chance worth risking, given how many lives are left.
     *
     * <p>Three or more is comfortable, so expected gold does the sorting. Two means one bad turn
     * from the edge. One means a single failure ends the run and everything it would still have
     * earned, so only the top of the scale gets through.
     */
    private static double survivalFloor(int lives) {
        return switch (lives) {
            case 0, 1 -> 0.80;
            case 2 -> 0.60;
            default -> 0.0;
        };
    }
}
