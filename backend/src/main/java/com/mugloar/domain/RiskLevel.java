package com.mugloar.domain;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The risk scale.
 *
 * <p>The API hands back a free-text label like "Piece of cake" or "Suicide mission" and never
 * documents the set, so the ordering below came from playing a few hundred turns against the live
 * API and recording how often each label actually succeeded. Declaration order is the scale: safest
 * first. {@code ordinal()} is therefore meaningful and the strategies rely on it.
 *
 * <p>{@code basePrior} is the measured success rate at dragon level 0. It is a prior, not a
 * promise - the real chance also moves with dragon level, which {@link SuccessModel} folds in.
 *
 * <p>"Impossible" only ever showed up on encrypted ads, which is why it is easy to miss if you only
 * look at plaintext messages.
 */
public enum RiskLevel {
    PIECE_OF_CAKE("Piece of cake", 0.86),
    WALK_IN_THE_PARK("Walk in the park", 0.82),
    SURE_THING("Sure thing", 0.80),
    QUITE_LIKELY("Quite likely", 0.68),
    HMMM("Hmmm....", 0.52),
    GAMBLE("Gamble", 0.40),
    RISKY("Risky", 0.34),
    PLAYING_WITH_FIRE("Playing with fire", 0.24),
    RATHER_DETRIMENTAL("Rather detrimental", 0.17),
    SUICIDE_MISSION("Suicide mission", 0.10),
    IMPOSSIBLE("Impossible", 0.05),

    /**
     * Anything the API invents that we have not seen before. Treated as worse than
     * {@link #IMPOSSIBLE} so an unknown label can never look attractive to a strategy.
     */
    UNKNOWN("", 0.02);

    private static final Map<String, RiskLevel> BY_LABEL = Stream.of(values())
            .filter(r -> !r.label.isEmpty())
            .collect(Collectors.toMap(r -> normalise(r.label), Function.identity()));

    private final String label;
    private final double basePrior;

    RiskLevel(String label, double basePrior) {
        this.label = label;
        this.basePrior = basePrior;
    }

    /** The label exactly as the Mugloar API spells it. */
    public String label() {
        return label;
    }

    /** Measured success rate at dragon level 0. */
    public double basePrior() {
        return basePrior;
    }

    /** 0.0 for the safest label, 1.0 for the most dangerous one. */
    public double normalisedRisk() {
        return (double) ordinal() / (values().length - 1);
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /**
     * Maps a wire label onto the scale. Unknown or missing labels fall back to {@link #UNKNOWN}
     * rather than throwing, because a new label is a reason to play cautiously, not to crash a run.
     */
    public static RiskLevel fromLabel(String wireLabel) {
        return Optional.ofNullable(wireLabel)
                .map(RiskLevel::normalise)
                .map(BY_LABEL::get)
                .orElse(UNKNOWN);
    }

    /** The API is inconsistent about trailing dots and casing ("Hmmm...." vs "Hmmm..."). */
    private static String normalise(String raw) {
        return raw.strip().toLowerCase().replaceAll("\\.+$", "");
    }
}
