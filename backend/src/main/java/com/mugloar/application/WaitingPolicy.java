package com.mugloar.application;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import java.util.Optional;

/**
 * Decides whether giving up the turn is worthwhile. The reputation call consumes a turn and risks
 * nothing, which makes it a pass. Waiting replaces nothing on the board: it only ages every ad by
 * one turn, so the board changes only when an ad expires. Waiting therefore has to cover the
 * soonest expiry on the board, and is refused when the remaining budget cannot.
 */
public final class WaitingPolicy {

    private final int maxIdleTurns;

    /** @param maxIdleTurns turns a single game may spend waiting in total; zero disables it */
    public WaitingPolicy(int maxIdleTurns) {
        this.maxIdleTurns = Math.max(0, maxIdleTurns);
    }

    public int budget() {
        return maxIdleTurns;
    }

    public int remainingBudget(GameMemory memory) {
        return maxIdleTurns - memory.idlesUsed();
    }

    /** The reason to wait this turn, or empty. Consulted only after the strategy and shop policy have both declined. */
    public Optional<String> reasonToWait(GameState state, Board board, GameMemory memory) {
        int remaining = remainingBudget(memory);
        if (remaining <= 0) {
            return Optional.empty();
        }
        int turnsUntilBoardChanges = board.ads().stream()
                .mapToInt(Ad::expiresIn)
                .min()
                // An empty board has nothing to wait out, but nothing to attempt either.
                .orElse(0);
        if (turnsUntilBoardChanges > remaining) {
            return Optional.empty();
        }
        return Optional.of("Nothing worth attempting at %d %s"
                .formatted(state.lives(), state.lives() == 1 ? "life" : "lives"));
    }
}
