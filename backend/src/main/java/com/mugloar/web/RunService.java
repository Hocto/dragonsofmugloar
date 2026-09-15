package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.GameMemories;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopDecision;
import com.mugloar.application.TurnEvent;
import com.mugloar.domain.GameState;
import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.TurnResultView;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The request/response surface for runs: start one, look at one, make a manual move on one.
 *
 * <p>Automatic play is {@link AutoPlayer}'s job. This class hands an auto run over and does not see
 * it again; a manual run it drives one call at a time.
 */
@Service
public class RunService {

    private static final Board NO_BOARD =
            new Board(List.of(), List.of(), List.of(), new ShopDecision.Skip("Run is over"));

    private final GameOrchestrator orchestrator;
    private final GameMemories memories;
    private final AutoPlayer autoPlayer;
    private final RunRegistry registry;
    private final RunViewMapper mapper;

    public RunService(
            GameOrchestrator orchestrator,
            GameMemories memories,
            AutoPlayer autoPlayer,
            RunRegistry registry,
            RunViewMapper mapper) {
        this.orchestrator = orchestrator;
        this.memories = memories;
        this.autoPlayer = autoPlayer;
        this.registry = registry;
        this.mapper = mapper;
    }

    public RunView start(RunMode mode) {
        GameState state = orchestrator.start();
        Run run = registry.register(state, mode, orchestrator.strategyName());
        run.record(TurnEvent.started(state));
        run.board(orchestrator.board(state));
        if (mode == RunMode.AUTO) {
            autoPlayer.play(run);
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

    /** The same move the strategy makes when the board is hopeless, available to a person too. */
    public TurnResultView waitOutTurn(String runId) {
        Run run = registry.require(runId);
        synchronized (run) {
            requireManualAndRunning(run);
            return afterManualTurn(run, orchestrator.waitOutTurn(run.state(), run.nextSequence()));
        }
    }

    public Run require(String runId) {
        return registry.require(runId);
    }

    private TurnResultView afterManualTurn(Run run, TurnEvent event) {
        run.record(event);
        memories.reputationOf(run.id()).ifPresent(run::reputation);
        if (run.state().isOver() && run.isRunning()) {
            run.record(TurnEvent.finished(run.nextSequence(), run.state(), "Out of lives"));
        }
        if (run.isRunning()) {
            run.board(orchestrator.board(run.state()));
        } else {
            run.board(null);
            memories.forget(run.id());
        }
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
}
