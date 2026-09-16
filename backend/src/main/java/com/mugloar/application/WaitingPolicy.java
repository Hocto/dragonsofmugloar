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

    /** Turns until the soonest notice expires; zero for an empty board. */
    public static int turnsUntilBoardChanges(Board board) {
        return board.ads().stream().mapToInt(Ad::expiresIn).min().orElse(0);
    }

    /** Whether the remaining budget covers the turns the board needs. Applies to the bot and the player alike. */
    public boolean canAffordToWait(Board board, GameMemory memory) {
        int remaining = remainingBudget(memory);
        return remaining > 0 && turnsUntilBoardChanges(board) <= remaining;
    }

    /** The reason to wait this turn, or empty. Consulted only after the strategy and shop policy have both declined. */
    public Optional<String> reasonToWait(GameState state, Board board, GameMemory memory) {
        if (!canAffordToWait(board, memory)) {
            return Optional.empty();
        }
        return Optional.of("Nothing worth attempting at %d %s"
                .formatted(state.lives(), state.lives() == 1 ? "life" : "lives"));
    }
}
