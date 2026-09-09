package com.mugloar.application.strategy;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpectedValueStrategyTest {

    private final ExpectedValueStrategy strategy = new ExpectedValueStrategy(1.6);

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
    void getsStricterAsLivesRunOut() {
        Ad middling = ad("middling", 100, 3, RiskLevel.QUITE_LIKELY);

        assertThat(strategy.rank(List.of(middling), state(3, 0, 0))).isNotEmpty();
        assertThat(strategy.rank(List.of(middling), state(2, 0, 0))).isNotEmpty();
        assertThat(strategy.rank(List.of(middling), state(1, 0, 0))).isEmpty();
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
    void willNotTouchTheLabelsThatNeverPayOff() {
        // 1 success in 44 attempts for a suicide mission, 0 in 16 for impossible. A big reward is
        // exactly the trap here, so the filter runs before the reward is ever looked at.
        Ad hugeButHopeless = ad("hopeless", 5000, 3, RiskLevel.SUICIDE_MISSION);
        Ad modestButReal = ad("real", 40, 3, RiskLevel.HMMM);

        List<AdValuation> ranked = strategy.rank(List.of(hugeButHopeless, modestButReal), state(5, 0, 0));

        assertThat(ranked).extracting(v -> v.ad().adId()).containsExactly("real");
    }

    @Test
    void dragonLevelDoesNotChangeTheOdds() {
        // Level multiplies the rewards on the board, not the chance of success. The strategy reads
        // the reward it is given and nothing else, which is why these agree.
        Ad ad = ad("same", 100, 3, RiskLevel.HMMM);

        assertThat(strategy.rank(List.of(ad), state(5, 0, 0)).getFirst().score())
                .isEqualTo(strategy.rank(List.of(ad), state(5, 0, 20)).getFirst().score());
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

        assertThat(valuation.successChance()).isEqualTo(RiskLevel.SURE_THING.successRate());
        assertThat(valuation.expectedGold()).isEqualTo(100 * RiskLevel.SURE_THING.successRate());
        assertThat(valuation.urgency()).isEqualTo(1.0 + 1.6 / 2);
        assertThat(valuation.score()).isEqualTo(valuation.expectedGold() * valuation.urgency());
    }
}
