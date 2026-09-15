package com.mugloar.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.application.GameMemories;
import com.mugloar.application.TurnEvent;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The registry's cap is the only thing standing between an abandoned manual run and a permanent
 * entry in game memory, so the eviction is what these test.
 */
class RunRegistryTest {

    private static GameState game(String id) {
        return new GameState(id, 3, 0, 0, 0, 0, 1);
    }

    private static RunRegistry registryHolding(int maxRuns, GameMemories memories) {
        return new RunRegistry(memories,
                new WebProperties(List.of(), Duration.ZERO, maxRuns, Duration.ofMinutes(1)));
    }

    @Test
    void evictingARunForgetsItsGameMemoryToo() {
        // A manual run the player walks away from never reaches the code that forgets it. The
        // registry is the only thing that will ever let go of it, so it has to let go of both.
        GameMemories memories = new GameMemories();
        RunRegistry registry = registryHolding(2, memories);

        registry.register(game("abandoned"), RunMode.MANUAL, "expected-value");
        memories.update("abandoned", m -> m.afterIdling(Reputation.NEUTRAL));
        registry.register(game("second"), RunMode.MANUAL, "expected-value");
        registry.register(game("third"), RunMode.MANUAL, "expected-value");

        assertThat(registry.find("abandoned")).isEmpty();
        assertThat(memories.of("abandoned").idlesUsed()).isZero();
        assertThat(memories.size()).isZero();
    }

    @Test
    void prefersToEvictFinishedRunsBeforeLiveOnes() {
        GameMemories memories = new GameMemories();
        RunRegistry registry = registryHolding(2, memories);

        Run live = registry.register(game("live"), RunMode.AUTO, "expected-value");
        Run done = registry.register(game("done"), RunMode.AUTO, "expected-value");
        done.record(TurnEvent.finished(1, game("done"), "Out of lives"));

        registry.register(game("newcomer"), RunMode.AUTO, "expected-value");

        assertThat(registry.find("live")).isPresent();
        assertThat(registry.find("done")).isEmpty();
        assertThat(live.isRunning()).isTrue();
    }

    @Test
    void findsWhatItHoldsAndNothingElse() {
        RunRegistry registry = registryHolding(10, new GameMemories());
        registry.register(game("g1"), RunMode.MANUAL, "expected-value");

        assertThat(registry.find("g1")).isPresent();
        assertThat(registry.find("g2")).isEmpty();
    }
}
