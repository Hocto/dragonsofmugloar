package com.mugloar.web;

import com.mugloar.application.TurnAction;
import com.mugloar.application.TurnEvent;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One game, plus everything the UI needs to render it.
 *
 * <p>An auto run keeps playing whether or not a browser is attached, so the event history lives
 * here and a late subscriber gets a replay before the live events. That is the difference between a
 * page you can refresh and one you cannot.
 *
 * <p>The Mugloar game id doubles as the run id. There is no second identifier to keep in sync.
 */
public final class Run {

    private final RunMode mode;
    private final String strategy;
    private final Instant startedAt = Instant.now();
    private final List<TurnEvent> events = new ArrayList<>();
    private final List<Consumer<TurnEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    private volatile GameState state;
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Reputation reputation;
    private volatile String failure;

    Run(GameState state, RunMode mode, String strategy) {
        this.state = state;
        this.mode = mode;
        this.strategy = strategy;
    }

    public String id() {
        return state.gameId();
    }

    public RunMode mode() {
        return mode;
    }

    public String strategy() {
        return strategy;
    }

    public GameState state() {
        return state;
    }

    public RunStatus status() {
        return status;
    }

    public Reputation reputation() {
        return reputation;
    }

    public String failure() {
        return failure;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public synchronized List<TurnEvent> events() {
        return List.copyOf(events);
    }

    long nextSequence() {
        return sequence.incrementAndGet();
    }

    void reputation(Reputation latest) {
        this.reputation = latest;
    }

    /**
     * Records a turn and hands it to anyone streaming.
     *
     * <p>Only a terminal event closes a run. Lives hitting zero is not enough on its own - the
     * caller still emits an explicit FINISHED event, so a stream always ends with a clear last
     * message instead of just going quiet.
     */
    void record(TurnEvent event) {
        synchronized (this) {
            events.add(event);
            state = event.state();
            if (event.action() == TurnAction.FAILED) {
                status = RunStatus.FAILED;
                failure = event.description();
            } else if (event.action() == TurnAction.FINISHED) {
                status = RunStatus.FINISHED;
            }
        }
        listeners.forEach(listener -> listener.accept(event));
    }

    void onEvent(Consumer<TurnEvent> listener) {
        listeners.add(listener);
    }

    void removeListener(Consumer<TurnEvent> listener) {
        listeners.remove(listener);
    }

    public boolean isRunning() {
        return status == RunStatus.RUNNING;
    }
}
