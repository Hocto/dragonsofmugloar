package com.mugloar.application.strategy;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.SuccessModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpectedValueStrategyTest {

    private final ExpectedValueStrategy strategy =
            new ExpectedValueStrategy(SuccessModel.DEFAULT, 1.6);

    @Test
    void prefersRealisticGoldOverAdvertisedGold() {
        Ad longShot = ad("longshot", 200, 5, RiskLevel.SUICIDE_MISSION);
        Ad safeBet = ad("safe", 60, 5, RiskLevel.PIECE_OF_CAKE);

        AdValuation chosen = strategy.select(List.of(longShot, safeBet), state(3, 0, 0)).orElseThrow();

        assertThat(chosen.ad().adId()).isEqualTo("safe");
    }

    @Test
    void breaksTiesTowardsTheAdThatIsAboutToExpire() {
        Ad expiringNow = ad("now", 50, 1, RiskLevel.SURE_THING);
        Ad plentyOfTime = ad("later", 50, 7, RiskLevel.SURE_THING);

        AdValuation chosen =
                strategy.select(List.of(plentyOfTime, expiringNow), state(3, 0, 0)).orElseThrow();

        assertThat(chosen.ad().adId()).isEqualTo("now");
    }

    @Test
    void urgencyDoesNotOverrideAMuchBetterAd() {
        Ad expiringButWorthless = ad("crumbs", 4, 1, RiskLevel.PIECE_OF_CAKE);
        Ad valuableAndPatient = ad("prize", 120, 6, RiskLevel.PIECE_OF_CAKE);

        AdValuation chosen = strategy
                .select(List.of(expiringButWorthless, valuableAndPatient), state(3, 0, 0))
                .orElseThrow();

        assertThat(chosen.ad().adId()).isEqualTo("prize");
    }

    @Test
    void refusesGamblesOnTheLastLife() {
        GameState oneLifeLeft = state(1, 0, 0);
        Ad temptingButRisky = ad("risky", 300, 2, RiskLevel.RISKY);

        assertThat(strategy.rank(List.of(temptingButRisky), oneLifeLeft)).isEmpty();
    }

    @Test
    void stillPlaysTheSafeAdsOnTheLastLife() {
        GameState oneLifeLeft = state(1, 0, 0);
        Ad safe = ad("safe", 20, 3, RiskLevel.PIECE_OF_CAKE);
        Ad risky = ad("risky", 300, 3, RiskLevel.RISKY);

        List<AdValuation> ranked = strategy.rank(List.of(risky, safe), oneLifeLeft);

        assertThat(ranked).extracting(v -> v.ad().adId()).containsExactly("safe");
    }

    @Test
    void loosensUpAsLivesComeBack() {
        Ad gamble = ad("gamble", 150, 3, RiskLevel.GAMBLE);

        assertThat(strategy.rank(List.of(gamble), state(1, 0, 0))).isEmpty();
        assertThat(strategy.rank(List.of(gamble), state(3, 0, 0))).isNotEmpty();
    }

    @Test
    void aLevelledDragonWillTakeOnAdsItWouldHaveSkipped() {
        GameState twoLivesLevelZero = state(2, 0, 0);
        GameState twoLivesLevelSix = state(2, 0, 6);
        Ad hard = ad("hard", 150, 3, RiskLevel.RISKY);

        assertThat(strategy.rank(List.of(hard), twoLivesLevelZero)).isEmpty();
        assertThat(strategy.rank(List.of(hard), twoLivesLevelSix)).isNotEmpty();
    }

    @Test
    void neverTouchesAnAdWithALabelItDoesNotRecognise() {
        Ad mystery = ad("mystery", 999, 3, RiskLevel.UNKNOWN);

        assertThat(strategy.rank(List.of(mystery), state(3, 0, 0))).isEmpty();
    }

    @Test
    void reportsTheReasoningAlongsideTheChoice() {
        Ad only = ad("only", 100, 2, RiskLevel.SURE_THING);

        AdValuation valuation = strategy.select(List.of(only), state(3, 0, 0)).orElseThrow();

        assertThat(valuation.successChance()).isEqualTo(RiskLevel.SURE_THING.basePrior());
        assertThat(valuation.expectedGold()).isEqualTo(100 * RiskLevel.SURE_THING.basePrior());
        assertThat(valuation.urgency()).isEqualTo(1.0 + 1.6 / 2);
        assertThat(valuation.score()).isEqualTo(valuation.expectedGold() * valuation.urgency());
    }
}
