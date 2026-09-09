package com.mugloar.web;

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
 * In-memory home for active runs.
 *
 * <p>In memory is a deliberate limit, not an oversight: a run is a live conversation with Mugloar
 * and there is nothing worth restoring after a restart. The trade is that a restart drops
 * everything and a second instance would not see the first one's runs - fine for a single container,
 * and the first thing I would replace if this ever ran more than once (see the README).
 *
 * <p>The cap stops a long-lived container from holding every game anyone ever played.
 */
@Component
public class RunRegistry {

    private static final Logger log = LoggerFactory.getLogger(RunRegistry.class);

    private final ConcurrentMap<String, Run> runs = new ConcurrentHashMap<>();
    private final int maxRuns;

    public RunRegistry(WebProperties properties) {
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

    /** Drops the oldest finished runs first, and only falls back to live ones if it has to. */
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
            log.info("run.evicted gameId={} status={}", run.id(), run.status());
        });
    }
}
