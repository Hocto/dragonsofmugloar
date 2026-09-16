package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.GameMemories;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopDecision;
import com.mugloar.application.TurnEvent;
import com.mugloar.application.WaitingPolicy;
import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.TurnResultView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The request/response surface for runs: start, view, and manual moves. Automatic play is handed
 * to {@link AutoPlayer}.
 */
@Service
public class RunService {

    private static final Board NO_BOARD =
            new Board(List.of(), List.of(), List.of(), new ShopDecision.Skip("Run is over"));

    /** Notices expire within seven turns of arriving; this only guards against that ever not holding. */
    private static final int MAX_WAIT_TURNS = 10;

    private final GameOrchestrator orchestrator;
    private final GameMemories memories;
    private final WaitingPolicy waitingPolicy;
    private final AutoPlayer autoPlayer;
    private final RunRegistry registry;
    private final RunViewMapper mapper;

    public RunService(
            GameOrchestrator orchestrator,
            GameMemories memories,
            WaitingPolicy waitingPolicy,
            AutoPlayer autoPlayer,
            RunRegistry registry,
            RunViewMapper mapper) {
        this.orchestrator = orchestrator;
        this.memories = memories;
        this.waitingPolicy = waitingPolicy;
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

    /**
     * Passes turns until the board changes. A single pass replaces nothing on the board; it only
     * ages every notice by one turn, so waiting is only meaningful once something expires. Each
     * turn is recorded as its own event, and a failure part-way leaves the turns already spent.
     * The per-game waiting budget applies to the player exactly as it does to the strategy:
     * a wait the remaining budget cannot see through is refused rather than half-spent.
     */
    public TurnResultView waitForBoardToChange(String runId) {
        Run run = registry.require(runId);
        synchronized (run) {
            requireManualAndRunning(run);
            Board current = board(run);
            int remaining = waitTurnsRemaining(run);
            if (!waitingPolicy.canAffordToWait(current, memories.of(run.id()))) {
                throw new IllegalStateException(remaining <= 0
                        ? "No waiting turns left this game"
                        : "Only %d waiting %s left this game; the board needs %d".formatted(
                                remaining, remaining == 1 ? "turn" : "turns",
                                WaitingPolicy.turnsUntilBoardChanges(current)));
            }
            Set<String> before = adIds(current);
            TurnEvent last = null;
            int cap = Math.min(MAX_WAIT_TURNS, remaining);
            for (int turn = 0; turn < cap && run.isRunning(); turn++) {
                last = orchestrator.waitOutTurn(run.state(), run.nextSequence());
                run.record(last);
                memories.reputationOf(run.id()).ifPresent(run::reputation);
                Board fresh = orchestrator.board(run.state());
                run.board(fresh);
                if (!adIds(fresh).equals(before)) {
                    break;
                }
            }
            return new TurnResultView(last, view(run));
        }
    }

    private static Set<String> adIds(Board board) {
        return board.ads().stream().map(Ad::adId).collect(Collectors.toSet());
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
        return mapper.toView(run, run.isRunning() ? board(run) : NO_BOARD, waitTurnsRemaining(run));
    }

    private int waitTurnsRemaining(Run run) {
        return Math.max(0, waitingPolicy.remainingBudget(memories.of(run.id())));
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
