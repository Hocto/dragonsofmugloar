package com.mugloar.web;

import static com.mugloar.TestFixtures.ad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.FakeMugloarApi;
import com.mugloar.application.GameMemories;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopPolicy;
import com.mugloar.application.WaitingPolicy;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.application.strategy.ExpectedValueStrategy;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.ShopItem;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * No Spring. The service is assembled by hand from a scripted API so the one thing worth pinning
 * here - what is left behind when starting a run fails halfway - can be asserted directly.
 */
class RunServiceTest {

    private static final List<ShopItem> SHOP = List.of(new ShopItem("hpot", "Healing potion", 50));

    private static RunService serviceOver(FakeMugloarApi api, RunRegistry registry) {
        return serviceOver(api, registry, 10);
    }

    private static RunService serviceOver(FakeMugloarApi api, RunRegistry registry, int waitBudget) {
        GameMemories memories = new GameMemories();
        WaitingPolicy waiting = new WaitingPolicy(waitBudget);
        GameOrchestrator orchestrator = new GameOrchestrator(
                api, new ExpectedValueStrategy(1.6), new ShopPolicy(2, 50), waiting, memories);
        WebProperties web = new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1));
        return new RunService(orchestrator, memories, waiting,
                new AutoPlayer(orchestrator, memories, web), registry, new RunViewMapper());
    }

    private static RunRegistry registry() {
        return new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));
    }

    @Test
    void aStartThatFailsAfterTheGameExistsLeavesNothingInTheRegistry() {
        // Mugloar creates the game, then the first board fetch fails. Registering before that
        // fetch used to leave a RUNNING run with no player thread - a zombie a later visit to the
        // id would find and watch not move.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP)
                .failing("messages"::equals);
        RunRegistry registry = new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));

        assertThatThrownBy(() -> serviceOver(api, registry).start(RunMode.AUTO))
                .isInstanceOf(MugloarApiException.class);

        assertThat(registry.find(api.state().gameId())).isEmpty();
    }

    @Test
    void waitingPassesTurnsUntilANoticeExpiresAndRecordsEachOne() {
        // Two notices, expiring in 3 and 6. Waiting has to spend exactly three turns: the board
        // is unchanged after the first two passes and loses the first notice on the third.
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(ad("soon", 40, 3, RiskLevel.RISKY), ad("later", 40, 6, RiskLevel.RISKY)), SHOP);
        RunRegistry registry = new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));
        RunService service = serviceOver(api, registry);
        String runId = service.start(RunMode.MANUAL).runId();

        var result = service.waitForBoardToChange(runId);

        assertThat(result.run().state().turn()).isEqualTo(3);
        assertThat(result.run().ads()).extracting(a -> a.adId()).containsExactly("later");
        assertThat(result.run().summary().idled()).isEqualTo(3);
        assertThat(api.calls()).filteredOn("investigateReputation"::equals).hasSize(3);
        assertThat(result.event().action()).isEqualTo(com.mugloar.application.TurnAction.IDLED);
    }

    @Test
    void theWaitingBudgetIsPerGameAndRunsDownAcrossClicks() {
        // Budget 10. The first wait costs 3 turns and leaves 7; the second costs 3 and leaves 4.
        FakeMugloarApi api = new FakeMugloarApi(
                List.of(ad("a", 40, 3, RiskLevel.RISKY), ad("b", 40, 6, RiskLevel.RISKY)), SHOP);
        RunService service = serviceOver(api, registry(), 10);
        String runId = service.start(RunMode.MANUAL).runId();
        assertThat(service.view(runId).waitTurnsRemaining()).isEqualTo(10);

        assertThat(service.waitForBoardToChange(runId).run().waitTurnsRemaining()).isEqualTo(7);
        assertThat(service.waitForBoardToChange(runId).run().waitTurnsRemaining()).isEqualTo(4);
    }

    @Test
    void refusesToWaitWhenTheBudgetIsSpent() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 1, RiskLevel.RISKY)), SHOP);
        RunService service = serviceOver(api, registry(), 0);
        String runId = service.start(RunMode.MANUAL).runId();

        assertThatThrownBy(() -> service.waitForBoardToChange(runId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No waiting turns left");
        assertThat(api.calls()).doesNotContain("investigateReputation");
    }

    @Test
    void refusesAWaitTheBudgetCannotSeeThrough() {
        // Three turns of budget against a board that needs six. Spending the three would leave
        // the player poorer and on the same board, so it is refused up front, as it is for the bot.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("far", 40, 6, RiskLevel.RISKY)), SHOP);
        RunService service = serviceOver(api, registry(), 3);
        String runId = service.start(RunMode.MANUAL).runId();

        assertThatThrownBy(() -> service.waitForBoardToChange(runId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only 3 waiting turns left")
                .hasMessageContaining("needs 6");
        assertThat(service.view(runId).state().turn()).isZero();
    }

    @Test
    void waitingStopsAtTheSafetyCapIfTheBoardNeverChanges() {
        // Not a real board - nothing lives longer than seven turns - but the loop must terminate
        // even if that ever stops being true. The budget is far above the cap so the cap is what
        // stops it, not the budget.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("stuck", 40, 99, RiskLevel.RISKY)), SHOP);
        RunService service = serviceOver(api, registry(), 200);
        String runId = service.start(RunMode.MANUAL).runId();

        var result = service.waitForBoardToChange(runId);

        assertThat(result.run().state().turn()).isEqualTo(10);
        assertThat(api.calls()).filteredOn("investigateReputation"::equals).hasSize(10);
    }

    @Test
    void theLoopNeverSpendsMoreThanTheRemainingBudget() {
        // Budget 4 against a board that needs 4: affordable, and the loop must not overrun.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 4, RiskLevel.RISKY)), SHOP);
        RunService service = serviceOver(api, registry(), 4);
        String runId = service.start(RunMode.MANUAL).runId();

        var result = service.waitForBoardToChange(runId);

        assertThat(result.run().state().turn()).isEqualTo(4);
        assertThat(result.run().waitTurnsRemaining()).isZero();
        assertThatThrownBy(() -> service.waitForBoardToChange(runId))
                .hasMessageContaining("No waiting turns left");
    }

    @Test
    void aFailurePartWayThroughWaitingKeepsTheTurnsAlreadySpent() {
        // The third pass fails upstream. Two turns were really spent; the run must show them.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.RISKY)), SHOP);
        int[] reputationCalls = {0};
        api.failing(call -> "investigateReputation".equals(call) && ++reputationCalls[0] == 3);
        RunRegistry registry = new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));
        RunService service = serviceOver(api, registry);
        String runId = service.start(RunMode.MANUAL).runId();

        assertThatThrownBy(() -> service.waitForBoardToChange(runId))
                .isInstanceOf(MugloarApiException.class);

        Run run = registry.require(runId);
        assertThat(run.state().turn()).isEqualTo(2);
        assertThat(run.summary().idled()).isEqualTo(2);
        assertThat(run.isRunning()).isTrue();
    }

    @Test
    void aStartThatSucceedsIsRegisteredWithItsBoardAlreadyInHand() {
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("a", 40, 5, RiskLevel.PIECE_OF_CAKE)), SHOP);
        RunRegistry registry = new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));

        var view = serviceOver(api, registry).start(RunMode.MANUAL);

        Run run = registry.require(view.runId());
        assertThat(run.board()).isNotNull();
        assertThat(view.ads()).hasSize(1);
        assertThat(view.summary().solved()).isZero();
    }
}
