package com.mugloar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RiskLevelTest {

    @ParameterizedTest
    @CsvSource({
        "Piece of cake,PIECE_OF_CAKE",
        "Walk in the park,WALK_IN_THE_PARK",
        "Sure thing,SURE_THING",
        "Quite likely,QUITE_LIKELY",
        "Hmmm....,HMMM",
        "Gamble,GAMBLE",
        "Risky,RISKY",
        "Playing with fire,PLAYING_WITH_FIRE",
        "Rather detrimental,RATHER_DETRIMENTAL",
        "Suicide mission,SUICIDE_MISSION",
        "Impossible,IMPOSSIBLE"
    })
    void mapsEveryLabelTheApiSends(String label, RiskLevel expected) {
        assertThat(RiskLevel.fromLabel(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("trailing dots and casing vary between responses, so matching is lenient")
    @ValueSource(strings = {"Hmmm...", "Hmmm....", "hmmm.....", "  Hmmm....  "})
    void toleratesLabelPunctuation(String label) {
        assertThat(RiskLevel.fromLabel(label)).isEqualTo(RiskLevel.HMMM);
    }

    @Test
    void treatsUnrecognisedLabelsAsSomethingToLeaveAlone() {
        RiskLevel unknown = RiskLevel.fromLabel("Absolutely fine, trust me");

        assertThat(unknown).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(unknown.isKnown()).isFalse();
        assertThat(unknown.isWorthAttempting()).isFalse();
    }

    @Test
    void marksTheLabelsThatNeverPayOffAsNotWorthAttempting() {
        assertThat(RiskLevel.SUICIDE_MISSION.isWorthAttempting()).isFalse();
        assertThat(RiskLevel.IMPOSSIBLE.isWorthAttempting()).isFalse();
        assertThat(RiskLevel.PLAYING_WITH_FIRE.isWorthAttempting()).isTrue();
        assertThat(RiskLevel.PIECE_OF_CAKE.isWorthAttempting()).isTrue();
    }

    @Test
    void treatsAMissingLabelAsUnknownRatherThanThrowing() {
        assertThat(RiskLevel.fromLabel(null)).isEqualTo(RiskLevel.UNKNOWN);
    }

    @Test
    void ordersTheScaleFromSafestToMostDangerous() {
        RiskLevel[] scale = RiskLevel.values();

        for (int i = 1; i < scale.length - 1; i++) {
            assertThat(scale[i].successRate())
                    .as("%s should be no safer than %s", scale[i], scale[i - 1])
                    .isLessThanOrEqualTo(scale[i - 1].successRate());
        }
    }
}
