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
        GameMemories memories = new GameMemories();
        GameOrchestrator orchestrator = new GameOrchestrator(
                api, new ExpectedValueStrategy(1.6), new ShopPolicy(2, 50), new WaitingPolicy(10), memories);
        WebProperties web = new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1));
        return new RunService(orchestrator, memories, new AutoPlayer(orchestrator, memories, web),
                registry, new RunViewMapper());
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
    void waitingStopsAtTheSafetyCapIfTheBoardNeverChanges() {
        // Not a real board - nothing lives longer than seven turns - but the loop must terminate
        // even if that ever stops being true.
        FakeMugloarApi api = new FakeMugloarApi(List.of(ad("stuck", 40, 99, RiskLevel.RISKY)), SHOP);
        RunRegistry registry = new RunRegistry(new GameMemories(),
                new WebProperties(List.of(), Duration.ZERO, 10, Duration.ofMinutes(1)));
        RunService service = serviceOver(api, registry);
        String runId = service.start(RunMode.MANUAL).runId();

        var result = service.waitForBoardToChange(runId);

        assertThat(result.run().state().turn()).isEqualTo(10);
        assertThat(api.calls()).filteredOn("investigateReputation"::equals).hasSize(10);
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
