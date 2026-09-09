package com.mugloar.domain;

/**
 * An ad with the numbers that made it win or lose, kept together so the UI and the logs can show
 * the reasoning rather than just the verdict.
 *
 * @param expectedGold reward weighted by the estimated success chance
 * @param urgency      1.0 for an ad about to expire, lower for one that can wait
 * @param score        the final ranking number
 */
public record AdValuation(
        Ad ad,
        double successChance,
        double expectedGold,
        double urgency,
        double score) implements Comparable<AdValuation> {

    /** Highest score first. */
    @Override
    public int compareTo(AdValuation other) {
        return Double.compare(other.score, this.score);
    }
}
