package com.mugloar.domain;

/**
 * An ad together with the numbers that ranked it, so the UI and logs can show the reasoning.
 *
 * @param expectedGold reward weighted by the estimated success chance
 * @param urgency      higher for an ad about to expire
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
