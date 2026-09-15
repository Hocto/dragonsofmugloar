package com.mugloar.domain;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The risk scale, ordered by measured success rate. The API sends a free-text probability label and
 * does not document the set; declaration order is the scale, safest first, and {@code successRate}
 * is the observed rate over roughly 440 logged attempts:
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
 * <p>Two adjacent pairs ("Gamble"/"Quite likely", "Rather detrimental"/"Playing with fire") are
 * within sampling noise of each other and are kept in measured order. Dragon level does not change
 * the success rate; it scales the reward.
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

    /** Any label not in the scale. Rated at zero so it is never attractive and never attempted. */
    UNKNOWN("", 0.00);

    /** Labels below this rate are never attempted; the cutoff separates 0.31 from 0.02. */
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

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** False for labels below the cutoff and for anything unrecognised. */
    public boolean isWorthAttempting() {
        return successRate >= WORTH_ATTEMPTING;
    }

    /** Maps a wire label onto the scale; unknown or missing labels become {@link #UNKNOWN}. */
    public static RiskLevel fromLabel(String wireLabel) {
        return Optional.ofNullable(wireLabel)
                .map(RiskLevel::normalise)
                .map(BY_LABEL::get)
                .orElse(UNKNOWN);
    }

    /** Tolerates the API's inconsistent trailing dots and casing ("Hmmm...." vs "Hmmm..."). */
    private static String normalise(String raw) {
        return raw.strip().toLowerCase().replaceAll("\\.+$", "");
    }
}
