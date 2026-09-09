package com.mugloar.benchmark;

import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.TurnEvent;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.config.BenchmarkProperties;
import com.mugloar.domain.GameState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Plays N games headless and prints what actually happened.
 *
 * <p>Run it with {@code ./gradlew :backend:benchmark -Pgames=100}. It exists because "the strategy
 * feels better" is not a claim, and because comparing two strategies needs the same harness for
 * both.
 *
 * <p>Concurrency is capped by a semaphore rather than by the thread pool: Mugloar rate limits, and
 * pushing four games at once is roughly where 429s stop being the dominant cost.
 */
@Component
@Profile("benchmark")
public class BenchmarkRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final GameOrchestrator orchestrator;
    private final BenchmarkProperties properties;

    public BenchmarkRunner(GameOrchestrator orchestrator, BenchmarkProperties properties) {
        this.orchestrator = orchestrator;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        log.info("Playing {} games with strategy '{}' ({} at a time)",
                properties.games(), orchestrator.strategyName(), properties.concurrency());
        BenchmarkResult result = play(properties.games());
        print(result);
    }

    public BenchmarkResult play(int games) {
        long startedAt = System.currentTimeMillis();
        List<Integer> scores = Collections.synchronizedList(new ArrayList<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        Semaphore inFlight = new Semaphore(Math.max(1, properties.concurrency()));

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> pending = new ArrayList<>();
            for (int i = 0; i < games; i++) {
                int gameNumber = i + 1;
                pending.add(pool.submit(throttled(inFlight, () -> {
                    playOne(gameNumber, scores, failures);
                    return null;
                })));
            }
            for (Future<?> future : pending) {
                join(future);
            }
        }

        List<Integer> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);
        return new BenchmarkResult(
                sorted, List.copyOf(failures), properties.targetScore(),
                System.currentTimeMillis() - startedAt);
    }

    private void playOne(int gameNumber, List<Integer> scores, List<String> failures) {
        GameState state = null;
        try {
            state = orchestrator.start();
            long sequence = 0;
            while (!state.isOver()) {
                TurnEvent event = orchestrator.playTurn(state, ++sequence);
                state = event.state();
                if (event.endsRun()) {
                    break;
                }
            }
            scores.add(state.score());
            log.info("game {}/{} finished score={} turns={}",
                    gameNumber, properties.games(), state.score(), state.turn());
        } catch (MugloarApiException e) {
            // A run killed by the upstream is not a zero. Recording it as one would flatter the
            // average, so it goes in its own bucket and gets printed.
            failures.add("game %d: %s".formatted(gameNumber, e.getMessage()));
            log.warn("game {}/{} aborted at score={} status={}",
                    gameNumber, properties.games(), state == null ? 0 : state.score(), e.status());
        }
    }

    private static <T> Callable<T> throttled(Semaphore permits, Callable<T> task) {
        return () -> {
            permits.acquire();
            try {
                return task.call();
            } finally {
                permits.release();
            }
        };
    }

    private static void join(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            log.warn("benchmark game blew up", e.getCause());
        }
    }

    private void print(BenchmarkResult result) {
        String report = """

                Strategy       %s
                Games played   %d (%d aborted upstream)
                Average score  %.1f
                Median score   %d
                Min / Max      %d / %d
                p10 / p90      %d / %d
                Cleared %d     %.1f%% of runs
                Wall clock     %.1fs
                """.formatted(
                orchestrator.strategyName(),
                result.played(), result.failures().size(),
                result.average(),
                result.median(),
                result.min(), result.max(),
                result.percentile(10), result.percentile(90),
                result.targetScore(), result.shareClearingTarget() * 100,
                result.elapsedMillis() / 1000.0);
        log.info(report);
        result.failures().forEach(failure -> log.info("  aborted: {}", failure));
    }
}
