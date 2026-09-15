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
