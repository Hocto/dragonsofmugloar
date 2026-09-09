package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopDecision;
import com.mugloar.application.TurnEvent;
import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.config.StrategyProperties;
import com.mugloar.domain.GameState;
import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.TurnResultView;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Run lifecycle for the frontend.
 *
 * <p>An auto run gets its own thread and keeps playing whether or not anyone is watching, so
 * closing the tab does not abandon a game halfway and reopening it replays the whole thing. The
 * threads are virtual, so "one per run" is not a number worth worrying about - they spend their
 * lives blocked on Mugloar.
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private static final Board NO_BOARD =
            new Board(List.of(), List.of(), List.of(), new ShopDecision.Skip("Run is over"));

    private final GameOrchestrator orchestrator;
    private final MugloarApi api;
    private final RunRegistry registry;
    private final RunViewMapper mapper;
    private final Duration autoTurnDelay;
    private final int investigateEveryTurns;
    private final ExecutorService runners = Executors.newVirtualThreadPerTaskExecutor();

    public RunService(
            GameOrchestrator orchestrator,
            MugloarApi api,
            RunRegistry registry,
            RunViewMapper mapper,
            WebProperties webProperties,
            StrategyProperties strategyProperties) {
        this.orchestrator = orchestrator;
        this.api = api;
        this.registry = registry;
        this.mapper = mapper;
        this.autoTurnDelay = webProperties.autoTurnDelay();
        this.investigateEveryTurns = strategyProperties.investigateReputationEveryTurns();
    }

    public RunView start(RunMode mode) {
        GameState state = orchestrator.start();
        Run run = registry.register(state, mode, orchestrator.strategyName());
        run.record(TurnEvent.started(state));
        run.board(orchestrator.board(state));
        if (mode == RunMode.AUTO) {
            runners.submit(() -> playToTheEnd(run));
        }
        return view(run);
    }

    public RunView view(String runId) {
        return view(registry.require(runId));
    }

    /**
     * Manual turns are serialised per run.
     *
     * <p>The UI disables the board while a solve is in flight, but the API is reachable without the
     * UI, and two turns racing would spend two turns off one board and interleave their state
     * updates. One lock per run is cheap and there is only ever one player behind it.
     */
    public TurnResultView solve(String runId, String adId) {
        Run run = registry.require(runId);
        synchronized (run) {
            requireManualAndRunning(run);
            TurnEvent event =
                    orchestrator.solveById(run.state(), board(run), adId, run.nextSequence());
            return afterManualTurn(run, event);
        }
    }

    public TurnResultView buy(String runId, String itemId) {
        Run run = registry.require(runId);
        synchronized (run) {
            requireManualAndRunning(run);
            TurnEvent event =
                    orchestrator.buyById(run.state(), board(run), itemId, run.nextSequence());
            return afterManualTurn(run, event);
        }
    }

    public Run require(String runId) {
        return registry.require(runId);
    }

    private TurnResultView afterManualTurn(Run run, TurnEvent event) {
        run.record(event);
        finishIfDead(run);
        run.board(run.isRunning() ? orchestrator.board(run.state()) : null);
        return new TurnResultView(event, view(run));
    }

    private RunView view(Run run) {
        return mapper.toView(run, run.isRunning() ? board(run) : NO_BOARD);
    }

    /**
     * The board the run last acted on, fetched only if there is not one yet.
     *
     * <p>Nothing on the board moves between turns, so serving the cached copy is both cheaper and
     * no less correct. It matters because an auto run redraws the board on every streamed turn, and
     * refetching there would double this app's request rate into a per-IP limit for no new
     * information.
     */
    private Board board(Run run) {
        Board cached = run.board();
        if (cached != null) {
            return cached;
        }
        Board fresh = orchestrator.board(run.state());
        run.board(fresh);
        return fresh;
    }

    private static void requireManualAndRunning(Run run) {
        if (run.mode() != RunMode.MANUAL) {
            throw new IllegalStateException(
                    "Run " + run.id() + " is playing itself; watch the stream instead");
        }
        if (!run.isRunning()) {
            throw new IllegalStateException("Run " + run.id() + " is already over");
        }
    }

    private void playToTheEnd(Run run) {
        try {
            while (run.isRunning() && !run.state().isOver()) {
                Board board = orchestrator.board(run.state());
                run.board(board);
                run.record(orchestrator.playTurn(run.state(), board, run.nextSequence()));
                investigateIfDue(run);
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
            log.error("run.crashed gameId={}", run.id(), e);
            run.record(TurnEvent.failed(run.nextSequence(), run.state(), e.toString()));
        } finally {
            orchestrator.forget(run.id());
            run.board(null);
        }
    }

    /**
     * Reputation is informational only, and asking for it burns a turn, so it is off unless
     * explicitly configured. See the README for what the measurement said.
     */
    private void investigateIfDue(Run run) {
        int turn = run.state().turn();
        if (investigateEveryTurns <= 0 || turn == 0 || turn % investigateEveryTurns != 0) {
            return;
        }
        run.reputation(api.investigateReputation(run.id()));
        log.info("run.reputation gameId={} turn={} value={}", run.id(), run.state().turn(), run.reputation());
    }

    private void finishIfDead(Run run) {
        if (run.state().isOver() && run.isRunning()) {
            run.record(TurnEvent.finished(run.nextSequence(), run.state(), "Out of lives"));
        }
    }

    private void pause() {
        if (autoTurnDelay.isZero() || autoTurnDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(autoTurnDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
