package com.mugloar.application;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.FakeMugloarApi;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.application.strategy.ExpectedValueStrategy;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.ShopItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The turn loop, against a scripted Mugloar. This is the layer where the strategy, the shop policy
 * and the API meet, so the things worth asserting are the joins: that a shop decision pre-empts a
 * solve, that the board is not refetched when it does not need to be, and that a run driven to zero
 * lives stops rather than spinning.
 */
class GameOrchestratorTest {

    private static final List<ShopItem> SHOP = List.of(
            new ShopItem("hpot", "Healing potion", 50),
            new ShopItem("cs", "Claw Sharpening", 100));

    private GameOrchestrator orchestratorFor(FakeMugloarApi api) {
        return new GameOrchestrator(api, new ExpectedValueStrategy(1.6), new ShopPolicy(2, 50));
    }

    @Test
    void solvesTheHighestScoringAdAndReportsTheDelta() {
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("cheap", 10, 5, RiskLevel.PIECE_OF_CAKE),
                        ad("rich", 120, 5, RiskLevel.PIECE_OF_CAKE)),
                SHOP)
                .solvesWillGo(true);

        TurnEvent event = orchestratorFor(api).playTurn(state(3, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(event.target()).isEqualTo("rich");
        assertThat(event.success()).isTrue();
        assertThat(event.delta().gold()).isEqualTo(120);
        assertThat(event.delta().turn()).isEqualTo(1);
    }

    @Test
    void buysHealingInsteadOfSolvingWhenLivesAreLow() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("safe", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(2, 80, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.BOUGHT);
        assertThat(event.target()).isEqualTo("hpot");
        assertThat(event.delta().lives()).isEqualTo(1);
        assertThat(api.calls()).doesNotContain("solve:safe");
    }

    @Test
    void fetchesTheShopOnceAndTheBoardEveryTurn() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .solvesWillGo(true, true, true);
        GameOrchestrator orchestrator = orchestratorFor(api);

        GameState current = state(5, 0, 0);
        for (int turn = 1; turn <= 3; turn++) {
            current = orchestrator.playTurn(current, turn).state();
        }

        assertThat(api.calls()).filteredOn("shop"::equals).hasSize(1);
        assertThat(api.calls()).filteredOn("messages"::equals).hasSize(3);
    }

    @Test
    void takesTheSafestAdWhenTheStrategyRefusesEverything() {
        // One life, no gold for a potion, and nothing on the board clears the survival floor.
        // There is no way to skip a turn, so the least bad option is still the right move.
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("awful", 300, 3, RiskLevel.SUICIDE_MISSION),
                        ad("bad", 200, 3, RiskLevel.RISKY),
                        ad("less-bad", 30, 3, RiskLevel.SURE_THING)),
                SHOP)
                .solvesWillGo(true);

        TurnEvent event = orchestratorFor(api).playTurn(state(1, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(event.target()).isEqualTo("less-bad");
    }

    @Test
    void playsAWholeGameToTheEnd() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .solvesWillGo(true, false, false, false);
        GameOrchestrator orchestrator = orchestratorFor(api);

        GameState current = state(3, 0, 0);
        int turns = 0;
        while (!current.isOver() && turns < 20) {
            current = orchestrator.playTurn(current, ++turns).state();
        }

        assertThat(current.isOver()).isTrue();
        assertThat(current.score()).isEqualTo(40);
        // One win, then three failures that empty the life bar. Nothing spins past that.
        assertThat(turns).isEqualTo(4);
    }

    @Test
    void reportsAnEmptyBoardRatherThanThrowing() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(), SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(3, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.FAILED);
        assertThat(event.description()).contains("empty");
    }

    @Test
    void doesNotPlayAtAllOnceTheDragonIsDead() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(0, 0, 0), 7);

        assertThat(event.action()).isEqualTo(TurnAction.FINISHED);
        assertThat(api.calls()).isEmpty();
    }

    @Test
    void letsAnUpstreamFailureOutSoTheRunCanBeMarkedFailed() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .failing("messages"::equals);

        assertThatThrownBy(() -> orchestratorFor(api).playTurn(state(3, 0, 0), 1))
                .isInstanceOf(MugloarApiException.class);
    }

    @Test
    void manualSolveIgnoresTheStrategysOpinion() {
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("reckless", 300, 3, RiskLevel.RATHER_DETRIMENTAL),
                        ad("sensible", 30, 3, RiskLevel.PIECE_OF_CAKE)),
                SHOP)
                .solvesWillGo(false);

        TurnEvent event = orchestratorFor(api).solveById(state(1, 0, 0), "reckless", 1);

        assertThat(event.target()).isEqualTo("reckless");
        assertThat(event.success()).isFalse();
        assertThat(event.delta().lives()).isEqualTo(-1);
    }

    @Test
    void manualSolveOfSomethingNotOnTheBoardIsARequestError() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);

        assertThatThrownBy(() -> orchestratorFor(api).solveById(state(3, 0, 0), "ghost", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void forgettingAGameDropsItsCachedShop() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .solvesWillGo(true, true);
        GameOrchestrator orchestrator = orchestratorFor(api);

        GameState current = state(5, 0, 0);
        current = orchestrator.playTurn(current, 1).state();
        orchestrator.forget(current.gameId());
        orchestrator.playTurn(current, 2);

        assertThat(api.calls()).filteredOn("shop"::equals).hasSize(2);
    }
}
