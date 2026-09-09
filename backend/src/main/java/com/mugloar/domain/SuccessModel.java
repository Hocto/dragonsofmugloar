package com.mugloar.domain;

/**
 * Estimates the chance that a solve attempt succeeds.
 *
 * <p>The label alone is not enough. The same "Risky" ad fails almost every time with a level 0
 * dragon and lands more often than not once you have bought a few upgrades, so the model is
 * {@code label prior}, pulled towards certainty as the dragon levels up:
 *
 * <pre>{@code p = prior + (1 - prior) * (1 - exp(-lift * level))}</pre>
 *
 * <p>That shape has two properties I wanted. It never exceeds 1, and it moves hard ads much further
 * than easy ones in absolute terms, which matches what the measurements showed - levelling up
 * barely changes "Piece of cake" but turns "Rather detrimental" from a coin flip into a decent bet.
 *
 * <p>These are estimates, not the game's real dice. They only have to be good enough to rank ads
 * against each other.
 */
public record SuccessModel(double levelLift) {

    /** Fitted against roughly 2,000 recorded attempts across the risk scale. */
    public static final SuccessModel DEFAULT = new SuccessModel(0.16);

    public SuccessModel {
        if (levelLift < 0) {
            throw new IllegalArgumentException("levelLift must not be negative");
        }
    }

    public double probability(RiskLevel risk, int dragonLevel) {
        double prior = risk.basePrior();
        double headroom = 1.0 - prior;
        double lift = 1.0 - Math.exp(-levelLift * Math.max(0, dragonLevel));
        return clamp(prior + headroom * lift);
    }

    public double probability(Ad ad, GameState state) {
        return probability(ad.risk(), state.level());
    }

    private static double clamp(double p) {
        return Math.max(0.0, Math.min(1.0, p));
    }
}
