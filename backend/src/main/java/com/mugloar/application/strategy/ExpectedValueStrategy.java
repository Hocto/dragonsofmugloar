package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.SuccessModel;
import java.util.Comparator;
import java.util.List;

/**
 * The strategy that actually clears 1000.
 *
 * <p>Three ideas, in the order they matter:
 *
 * <ol>
 *   <li><b>Expected gold, not reward.</b> A 150 gold "Suicide mission" is worth less than a 40 gold
 *       "Sure thing", and how much less depends on the dragon level, so the chance comes from
 *       {@link SuccessModel} rather than from the label alone.
 *   <li><b>Urgency.</b> Ads expire. Two ads worth the same expected gold are not equally valuable if
 *       one disappears next turn and the other has six turns left - the second one will still be
 *       there after I have banked the first. This is the term that changed the results most.
 *   <li><b>A survival floor.</b> A failed attempt costs a life, and a run that dies stops scoring.
 *       On the last life the strategy will not touch anything below a high confidence bar, even if
 *       the expected gold looks great, and it drops out entirely rather than gamble - which is the
 *       orchestrator's cue to go buy a potion.
 * </ol>
 */
public final class ExpectedValueStrategy implements AdSelectionStrategy {

    public static final String NAME = "expected-value";

    private final SuccessModel successModel;
    private final double urgencyWeight;

    public ExpectedValueStrategy(SuccessModel successModel, double urgencyWeight) {
        this.successModel = successModel;
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
                .filter(ad -> ad.risk().isKnown())
                .map(ad -> valuate(ad, state))
                .filter(v -> v.successChance() >= floor)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private AdValuation valuate(Ad ad, GameState state) {
        double chance = successModel.probability(ad, state);
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
     * <p>Three lives is comfortable, so anything positive is fair game and the expected gold does
     * the sorting. Two lives means one bad turn away from the edge. One life means a single failure
     * ends the run, and the whole score with it, so only near certainties get through.
     */
    private static double survivalFloor(int lives) {
        return switch (lives) {
            case 0, 1 -> 0.75;
            case 2 -> 0.40;
            default -> 0.0;
        };
    }
}
