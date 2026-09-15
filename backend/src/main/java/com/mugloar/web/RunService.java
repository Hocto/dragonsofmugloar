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
 * The request/response surface for runs: start, view, and manual moves. Automatic play is handed
 * to {@link AutoPlayer}.
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
        // The board is fetched before the run is registered, so a failed fetch leaves nothing
        // behind in the registry.
        Board board = orchestrator.board(state);
        Run run = registry.register(state, mode, orchestrator.strategyName());
        run.record(TurnEvent.started(state));
        run.board(board);
        if (mode == RunMode.AUTO) {
            autoPlayer.play(run);
        }
        return view(run);
    }

    public RunView view(String runId) {
        return view(registry.require(runId));
    }

    /** Manual turns are serialised per run, so two concurrent requests cannot spend two turns off one board. */
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

    /** Gives up the turn, the same move the strategy makes on a hopeless board. */
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

    /** The cached board, fetched only if absent. Nothing on it changes between turns, and the upstream rate limits per IP. */
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
