package com.mugloar.application.strategy;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Ad;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.SuccessModel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The baseline. These tests pin down the behaviour that makes it lose, so the comparison in the
 * README is about something specific rather than a vibe.
 */
class RewardPerRiskStrategyTest {

    private final RewardPerRiskStrategy strategy = new RewardPerRiskStrategy(SuccessModel.DEFAULT);

    @Test
    void ignoresExpiryEntirely() {
        Ad expiringNow = ad("now", 50, 1, RiskLevel.SURE_THING);
        Ad plentyOfTime = ad("later", 50, 7, RiskLevel.SURE_THING);

        List<String> order = strategy.rank(List.of(expiringNow, plentyOfTime), state(3, 0, 0))
                .stream().map(v -> v.ad().adId()).toList();

        // Same reward, same risk, so the ad about to vanish gets no preference at all.
        assertThat(order).containsExactly("now", "later");
        assertThat(strategy.rank(List.of(expiringNow), state(3, 0, 0)).getFirst().score())
                .isEqualTo(strategy.rank(List.of(plentyOfTime), state(3, 0, 0)).getFirst().score());
    }

    @Test
    void willWalkIntoASuicideMissionOnTheLastLifeIfTheRewardIsBigEnough() {
        Ad longShot = ad("longshot", 2000, 3, RiskLevel.SUICIDE_MISSION);
        Ad safeBet = ad("safe", 30, 3, RiskLevel.PIECE_OF_CAKE);

        assertThat(strategy.select(List.of(longShot, safeBet), state(1, 0, 0)).orElseThrow()
                .ad().adId()).isEqualTo("longshot");
    }

    @Test
    void skipsUnknownLabels() {
        assertThat(strategy.rank(List.of(ad("mystery", 500, 3, RiskLevel.UNKNOWN)), state(3, 0, 0)))
                .isEmpty();
    }
}
