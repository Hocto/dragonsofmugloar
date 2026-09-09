package com.mugloar.domain;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The risk scale, ordered by measured success rate.
 *
 * <p>The API hands back a free-text probability label and never documents the set, so I logged
 * every attempt the bot made across a few dozen games and counted. Declaration order is the scale,
 * safest first, and {@code successRate} is the observed rate over roughly 440 attempts:
 *
 * <pre>
 *   Piece of cake       80/88   0.909
 *   Walk in the park    59/70   0.843
 *   Sure thing          26/35   0.743
 *   Gamble              18/27   0.667
 *   Quite likely        35/53   0.660
 *   Hmmm....            22/43   0.512
 *   Risky               13/26   0.500
 *   Rather detrimental   8/24   0.333
 *   Playing with fire    5/16   0.312
 *   Suicide mission      1/44   0.023
 *   Impossible           0/16   0.000
 * </pre>
 *
 * <p>Two adjacent pairs came out the wrong way round from what the words suggest - "Gamble" beat
 * "Quite likely", and "Rather detrimental" beat "Playing with fire" - by margins well inside the
 * sampling noise at those counts. I left them where the measurement put them rather than tidying
 * them into the order intuition wanted, because I have data for one and a hunch for the other.
 *
 * <p>The bottom two are the important entry. "Suicide mission" went one for forty-four and
 * "Impossible" zero for sixteen, and those attempts cost a life each. They are not long shots, they
 * are a leak, which is what {@link #isWorthAttempting()} exists to stop.
 *
 * <p>I also tried making the rate a function of dragon level. It is not - see {@link Ad}'s reward
 * instead, and the README.
 */
public enum RiskLevel {
    PIECE_OF_CAKE("Piece of cake", 0.91),
    WALK_IN_THE_PARK("Walk in the park", 0.84),
    SURE_THING("Sure thing", 0.74),
    GAMBLE("Gamble", 0.67),
    QUITE_LIKELY("Quite likely", 0.66),
    HMMM("Hmmm....", 0.51),
    RISKY("Risky", 0.50),
    RATHER_DETRIMENTAL("Rather detrimental", 0.33),
    PLAYING_WITH_FIRE("Playing with fire", 0.31),
    SUICIDE_MISSION("Suicide mission", 0.02),
    IMPOSSIBLE("Impossible", 0.00),

    /**
     * Anything the API invents that we have not seen before. Rated at zero so a new label can never
     * look attractive, and so it fails {@link #isWorthAttempting()} like the hopeless ones do.
     */
    UNKNOWN("", 0.00);

    /**
     * Below this, an attempt is a life thrown away. Set between "Playing with fire" at 0.31, which
     * pays for itself on the high rewards a levelled dragon sees, and "Suicide mission" at 0.02,
     * which never does.
     */
    private static final double WORTH_ATTEMPTING = 0.10;

    private static final Map<String, RiskLevel> BY_LABEL = Stream.of(values())
            .filter(risk -> !risk.label.isEmpty())
            .collect(Collectors.toMap(risk -> normalise(risk.label), Function.identity()));

    private final String label;
    private final double successRate;

    RiskLevel(String label, double successRate) {
        this.label = label;
        this.successRate = successRate;
    }

    /** The label exactly as the Mugloar API spells it. */
    public String label() {
        return label;
    }

    /** Measured share of attempts that succeeded. */
    public double successRate() {
        return successRate;
    }

    /** 0.0 for the safest label, 1.0 for the most dangerous one. */
    public double normalisedRisk() {
        return (double) ordinal() / (values().length - 1);
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** False for the labels that have never meaningfully paid off, and for anything unrecognised. */
    public boolean isWorthAttempting() {
        return successRate >= WORTH_ATTEMPTING;
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
