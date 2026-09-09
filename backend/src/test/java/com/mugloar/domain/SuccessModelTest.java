package com.mugloar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SuccessModelTest {

    private final SuccessModel model = SuccessModel.DEFAULT;

    @Test
    void atLevelZeroTheEstimateIsJustTheLabelPrior() {
        assertThat(model.probability(RiskLevel.RISKY, 0))
                .isCloseTo(RiskLevel.RISKY.basePrior(), within(1e-9));
    }

    @Test
    void levellingUpImprovesEveryLabel() {
        for (RiskLevel risk : RiskLevel.values()) {
            assertThat(model.probability(risk, 5))
                    .as("%s should improve with levels", risk)
                    .isGreaterThan(model.probability(risk, 0));
        }
    }

    @Test
    void levellingUpHelpsHardAdsMoreThanEasyOnes() {
        double easyGain = model.probability(RiskLevel.PIECE_OF_CAKE, 6)
                - model.probability(RiskLevel.PIECE_OF_CAKE, 0);
        double hardGain = model.probability(RiskLevel.RATHER_DETRIMENTAL, 6)
                - model.probability(RiskLevel.RATHER_DETRIMENTAL, 0);

        assertThat(hardGain).isGreaterThan(easyGain);
    }

    @Test
    void neverEscapesZeroToOne() {
        for (RiskLevel risk : RiskLevel.values()) {
            for (int level : new int[] {0, 1, 10, 500}) {
                assertThat(model.probability(risk, level)).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void treatsANegativeLevelAsZeroRatherThanBlowingUp() {
        assertThat(model.probability(RiskLevel.GAMBLE, -3))
                .isEqualTo(model.probability(RiskLevel.GAMBLE, 0));
    }
}
