package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.TurnAction;
import com.mugloar.application.TurnEvent;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import com.mugloar.web.dto.RunSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One game and everything the UI needs to render it. The event history lives here so a late
 * subscriber receives a replay before live events. The Mugloar game id is the run id.
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
    /** The board this run last acted on, cached so rendering costs no upstream call; the upstream rate limits by IP. */
    private volatile Board board;

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

    /** Counts over every event, not the capped view. */
    public synchronized RunSummary summary() {
        int solved = 0;
        int failed = 0;
        int bought = 0;
        int idled = 0;
        for (TurnEvent event : events) {
            switch (event.action()) {
                case SOLVED -> {
                    if (event.success()) {
                        solved++;
                    } else {
                        failed++;
                    }
                }
                case BOUGHT -> {
                    if (event.success()) {
                        bought++;
                    }
                }
                case IDLED -> idled++;
                default -> {
                }
            }
        }
        return new RunSummary(solved, failed, bought, idled);
    }

    long nextSequence() {
        return sequence.incrementAndGet();
    }

    void reputation(Reputation latest) {
        this.reputation = latest;
    }

    public Board board() {
        return board;
    }

    void board(Board latest) {
        this.board = latest;
    }

    /** Records a turn and notifies subscribers. Only a FINISHED or FAILED event closes the run. */
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

    /**
     * Snapshots the history and subscribes under one lock. Done separately, a turn recorded in
     * between would reach neither; under one lock the worst case is a duplicate, which the client
     * discards by sequence number.
     */
    synchronized List<TurnEvent> replayAndSubscribe(Consumer<TurnEvent> listener) {
        listeners.add(listener);
        return List.copyOf(events);
    }

    void removeListener(Consumer<TurnEvent> listener) {
        listeners.remove(listener);
    }

    public boolean isRunning() {
        return status == RunStatus.RUNNING;
    }
}
