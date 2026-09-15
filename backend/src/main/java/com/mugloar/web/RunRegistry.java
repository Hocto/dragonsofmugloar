package com.mugloar.web;

import com.mugloar.application.GameMemories;
import com.mugloar.domain.GameState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory store for runs. A restart drops every run, and a second instance would not see the
 * first one's. The cap bounds a long-lived process; when a run is evicted its game memory is
 * released with it, since an abandoned manual run never reaches the code that would otherwise
 * release it.
 */
@Component
public class RunRegistry {

    private static final Logger log = LoggerFactory.getLogger(RunRegistry.class);

    private final ConcurrentMap<String, Run> runs = new ConcurrentHashMap<>();
    private final GameMemories memories;
    private final int maxRuns;

    public RunRegistry(GameMemories memories, WebProperties properties) {
        this.memories = memories;
        this.maxRuns = properties.maxRuns();
    }

    public Run register(GameState state, RunMode mode, String strategy) {
        evictIfFull();
        Run run = new Run(state, mode, strategy);
        runs.put(run.id(), run);
        return run;
    }

    public Optional<Run> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public Run require(String runId) {
        return find(runId).orElseThrow(() -> new RunNotFoundException(runId));
    }

    /** Evicts finished runs oldest-first, and live runs only if there are no finished ones. */
    private void evictIfFull() {
        if (runs.size() < maxRuns) {
            return;
        }
        List<Run> candidates = runs.values().stream()
                .sorted(Comparator.comparing(Run::isRunning).thenComparing(Run::startedAt))
                .toList();
        int toDrop = runs.size() - maxRuns + 1;
        candidates.stream().limit(toDrop).forEach(run -> {
            runs.remove(run.id());
            memories.forget(run.id());
            log.info("run.evicted gameId={} status={}", run.id(), run.status());
        });
    }
}
