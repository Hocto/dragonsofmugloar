package com.mugloar.application;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.FakeMugloarApi;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.application.strategy.ExpectedValueStrategy;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
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
        return orchestratorFor(api, 20);
    }

    private GameOrchestrator orchestratorFor(FakeMugloarApi api, int maxIdleTurns) {
        return new GameOrchestrator(
                api, new ExpectedValueStrategy(1.6), new ShopPolicy(2, 50), maxIdleTurns);
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
    void buysWhatTheCallerNamedInManualMode() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);
        GameOrchestrator orchestrator = orchestratorFor(api);
        GameState current = state(4, 400, 0);

        TurnEvent event = orchestrator.buyById(current, orchestrator.board(current), "cs", 1);

        assertThat(event.action()).isEqualTo(TurnAction.BOUGHT);
        assertThat(event.target()).isEqualTo("cs");
        assertThat(event.delta().level()).isEqualTo(1);
        assertThat(event.delta().gold()).isEqualTo(-100);
    }

    @Test
    void playsFromABoardTheCallerAlreadyHasWithoutRefetchingIt() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .solvesWillGo(true);
        GameOrchestrator orchestrator = orchestratorFor(api);
        GameState current = state(5, 0, 0);
        Board board = orchestrator.board(current);

        orchestrator.playTurn(current, board, 1);

        // One read of the message board in total: the one the caller did.
        assertThat(api.calls()).filteredOn("messages"::equals).hasSize(1);
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
    void waitsOutTheTurnRatherThanGamblingItsLastLife() {
        // One life, no gold for a potion, nothing on the board above the survival floor, and the
        // soonest expiry is three turns away - well inside the budget, so waiting can reach it.
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("awful", 300, 3, RiskLevel.SUICIDE_MISSION),
                        ad("bad", 200, 3, RiskLevel.RISKY)),
                SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(1, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.IDLED);
        assertThat(api.calls()).contains("investigateReputation");
        assertThat(api.calls()).noneMatch(call -> call.startsWith("solve:"));
        assertThat(event.description()).startsWith("Nothing worth attempting at 1 life");
    }

    @Test
    void waitingSpendsATurnAndNothingElse() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("bad", 200, 3, RiskLevel.RISKY)), SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(1, 40, 3), 1);

        // The reputation endpoint reports no state, so the turn has to be advanced locally.
        assertThat(event.delta().turn()).isEqualTo(1);
        assertThat(event.delta().lives()).isZero();
        assertThat(event.delta().gold()).isZero();
        assertThat(event.state().turn()).isEqualTo(state(1, 40, 3).turn() + 1);
        assertThat(event.state().level()).isEqualTo(3);
    }

    @Test
    void takesTheSafestAdOnceTheWaitingBudgetIsSpent() {
        // The budget is cumulative over the whole game, not per bad board. Waiting out one board
        // leaves less in hand for the next, and eventually there is not enough to outlast anything.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("awful", 300, 2, RiskLevel.SUICIDE_MISSION)), SHOP)
                .solvesWillGo(true);
        GameOrchestrator orchestrator = orchestratorFor(api, 2);

        GameState current = state(1, 0, 0);
        for (int turn = 1; turn <= 2; turn++) {
            TurnEvent event = orchestrator.playTurn(current, turn);
            assertThat(event.action()).as("turn %d", turn).isEqualTo(TurnAction.IDLED);
            current = event.state();
        }

        // Budget spent. A fresh board it cannot outlast has to be played rather than waited out.
        api.setBoard(List.of(
                ad("still-awful", 300, 5, RiskLevel.SUICIDE_MISSION),
                ad("less-bad", 30, 5, RiskLevel.SURE_THING)));

        TurnEvent afterwards = orchestrator.playTurn(current, 3);
        assertThat(afterwards.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(afterwards.target()).isEqualTo("less-bad");
    }

    @Test
    void neverWaitsWhenWaitingIsTurnedOff() {
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(ad("less-bad", 30, 3, RiskLevel.SURE_THING),
                        ad("awful", 300, 3, RiskLevel.SUICIDE_MISSION)),
                SHOP)
                .solvesWillGo(true);

        TurnEvent event = orchestratorFor(api, 0).playTurn(state(1, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(api.calls()).doesNotContain("investigateReputation");
    }

    @Test
    void doesNotWaitWhenThereIsSomethingWorthAttempting() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("safe", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .solvesWillGo(true);

        TurnEvent event = orchestratorFor(api).playTurn(state(1, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(api.calls()).doesNotContain("investigateReputation");
    }

    @Test
    void prefersBuyingAPotionOverWaiting() {
        // Waiting buys a turn; a potion buys a life. The shop policy runs first for that reason.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("bad", 200, 3, RiskLevel.RISKY)), SHOP);

        TurnEvent event = orchestratorFor(api).playTurn(state(1, 80, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.BOUGHT);
        assertThat(event.target()).isEqualTo("hpot");
        assertThat(api.calls()).doesNotContain("investigateReputation");
    }

    @Test
    void remembersTheReputationItReadWhileWaiting() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("bad", 200, 3, RiskLevel.RISKY)), SHOP);
        GameOrchestrator orchestrator = orchestratorFor(api);
        GameState current = state(1, 0, 0);

        assertThat(orchestrator.reputationFor(current.gameId())).isEmpty();
        orchestrator.playTurn(current, 1);

        assertThat(orchestrator.reputationFor(current.gameId())).contains(Reputation.NEUTRAL);
    }

    @Test
    void waitsRatherThanFailingWhenTheBoardIsEmpty() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(), SHOP);

        assertThat(orchestratorFor(api).playTurn(state(3, 0, 0), 1).action())
                .isEqualTo(TurnAction.IDLED);
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
    void reportsAnEmptyBoardRatherThanThrowingOnceItCannotEvenWait() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(), SHOP);

        TurnEvent event = orchestratorFor(api, 0).playTurn(state(3, 0, 0), 1);

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

        GameOrchestrator orchestrator = orchestratorFor(api);
        GameState current = state(1, 0, 0);
        TurnEvent event =
                orchestrator.solveById(current, orchestrator.board(current), "reckless", 1);

        assertThat(event.target()).isEqualTo("reckless");
        assertThat(event.success()).isFalse();
        assertThat(event.delta().lives()).isEqualTo(-1);
    }

    @Test
    void aPersonCanGiveUpTheTurnEvenWithTheBotsBudgetSpent() {
        // The budget stops an automatic run looping. Someone clicking the button has decided.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("bad", 200, 3, RiskLevel.RISKY)), SHOP);
        GameOrchestrator orchestrator = orchestratorFor(api, 0);
        GameState current = state(1, 0, 0);

        TurnEvent event = orchestrator.waitOutTurn(current, 1);

        assertThat(event.action()).isEqualTo(TurnAction.IDLED);
        assertThat(event.delta().turn()).isEqualTo(1);
        assertThat(event.delta().lives()).isZero();
        assertThat(api.calls()).contains("investigateReputation");
        // A person's own choice is not explained back to them as the bot's reasoning.
        assertThat(event.description()).startsWith("Let the turn pass");
        assertThat(event.description()).doesNotContain("Nothing worth attempting");
    }

    @Test
    void willNotWaitForABoardItCannotOutlast() {
        // Waiting drops nothing from the board, so nothing new arrives; all it does is tick every
        // expiry down by one. With a budget of three and the soonest expiry six turns out, waiting
        // spends every turn it has and still ends up taking the same bad ad.
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("awful", 300, 6, RiskLevel.SUICIDE_MISSION),
                        ad("bad", 200, 6, RiskLevel.RISKY)),
                SHOP)
                .solvesWillGo(true);

        TurnEvent event = orchestratorFor(api, 3).playTurn(state(1, 0, 0), 1);

        assertThat(event.action()).isEqualTo(TurnAction.SOLVED);
        assertThat(event.target()).isEqualTo("bad");
        assertThat(api.calls()).doesNotContain("investigateReputation");
    }

    @Test
    void waitsWhenTheBudgetJustCoversTheSoonestExpiry() {
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(
                        ad("awful", 300, 6, RiskLevel.SUICIDE_MISSION),
                        ad("bad", 200, 3, RiskLevel.RISKY)),
                SHOP)
                .solvesWillGo(true);

        // Budget 3, soonest expiry 3: one ad will drop and be replaced, which is the whole point.
        assertThat(orchestratorFor(api, 3).playTurn(state(1, 0, 0), 1).action())
                .isEqualTo(TurnAction.IDLED);
    }

    @Test
    void stopsWaitingAsTheBudgetRunsDownBelowTheExpiry() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("bad", 200, 3, RiskLevel.RISKY)), SHOP)
                .solvesWillGo(true);
        GameOrchestrator orchestrator = orchestratorFor(api, 3);

        // Budget and expiry fall together, one per turn, so waiting stays affordable right up to
        // the point the ad expires and the board finally changes.
        GameState current = state(1, 0, 0);
        for (int turn = 1; turn <= 3; turn++) {
            TurnEvent event = orchestrator.playTurn(current, turn);
            assertThat(event.action()).as("turn %d", turn).isEqualTo(TurnAction.IDLED);
            current = event.state();
        }

        // The ad has now expired off the board, leaving nothing to attempt or wait for.
        assertThat(orchestrator.playTurn(current, 4).action()).isEqualTo(TurnAction.FAILED);
    }

    @Test
    void manualSolveOfSomethingNotOnTheBoardIsARequestError() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);

        GameOrchestrator orchestrator = orchestratorFor(api);
        GameState current = state(3, 0, 0);
        Board board = orchestrator.board(current);

        assertThatThrownBy(() -> orchestrator.solveById(current, board, "ghost", 1))
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
