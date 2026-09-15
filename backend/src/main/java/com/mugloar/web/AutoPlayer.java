package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.GameMemories;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.TurnEvent;
import com.mugloar.application.port.MugloarApiException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plays an automatic run to the end on its own thread.
 *
 * <p>The run keeps going whether or not anyone is watching, so closing the tab does not abandon a
 * game halfway and reopening it replays the whole thing. The threads are virtual, so "one per run"
 * is not a number worth worrying about - they spend their lives blocked on Mugloar.
 *
 * <p>Split out of {@link RunService} so that class is only the request/response surface for the
 * controller and this one is only the loop: pacing, finishing, and the three ways a run can end.
 */
@Service
public class AutoPlayer {

    private static final Logger log = LoggerFactory.getLogger(AutoPlayer.class);

    private final GameOrchestrator orchestrator;
    private final GameMemories memories;
    private final Duration turnDelay;
    private final ExecutorService runners = Executors.newVirtualThreadPerTaskExecutor();

    public AutoPlayer(GameOrchestrator orchestrator, GameMemories memories, WebProperties properties) {
        this.orchestrator = orchestrator;
        this.memories = memories;
        this.turnDelay = properties.autoTurnDelay();
    }

    /** Returns immediately; the run continues in the background and reports through the Run. */
    public void play(Run run) {
        runners.submit(() -> playToTheEnd(run));
    }

    private void playToTheEnd(Run run) {
        try {
            boolean firstTurn = true;
            while (run.isRunning() && !run.state().isOver()) {
                // start() already fetched a board to answer its own request with; play the first
                // turn from that one rather than asking Mugloar again a few milliseconds later.
                Board board = firstTurn && run.board() != null ? run.board() : orchestrator.board(run.state());
                firstTurn = false;
                run.board(board);
                run.record(orchestrator.playTurn(run.state(), board, run.nextSequence()));
                // Reputation is only ever read while waiting out a bad board, so this picks it up
                // whenever that has happened and leaves it null otherwise.
                memories.reputationOf(run.id()).ifPresent(run::reputation);
                pause();
            }
            if (run.isRunning()) {
                run.record(TurnEvent.finished(run.nextSequence(), run.state(), "Out of lives"));
            }
            log.info("run.finished gameId={} score={} turns={} strategy={}",
                    run.id(), run.state().score(), run.state().turn(), run.strategy());
        } catch (MugloarApiException e) {
            log.warn("run.failed gameId={} status={} reason=\"{}\"", run.id(), e.status(), e.getMessage());
            run.record(TurnEvent.failed(run.nextSequence(), run.state(), e.getMessage()));
        } catch (RuntimeException e) {
            // The detail goes to the log. The browser gets a sentence, not a stack trace.
            log.error("run.crashed gameId={}", run.id(), e);
            run.record(TurnEvent.failed(run.nextSequence(), run.state(),
                    "The run hit an error on our side and could not continue"));
        } finally {
            memories.forget(run.id());
            run.board(null);
        }
    }

    /** Slow enough that a person can read the turn feed, fast enough not to feel broken. */
    private void pause() {
        if (turnDelay.isZero() || turnDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(turnDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
