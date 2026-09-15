package com.mugloar.application;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import java.util.Optional;

/**
 * When giving up the turn is worth it.
 *
 * <p>There is no "pass" in this API, but a turn can still be spent on nothing - asking for the
 * player's reputation costs one and risks nothing. The question this class answers is whether that
 * is ever a better trade than attempting the least bad ad on a board the strategy has refused.
 *
 * <p>What waiting does to the board is less than it looks. The board holds ten ads; solving one
 * drops it and a replacement arrives, but waiting drops nothing, so nothing new arrives. All a turn
 * of waiting does is tick every expiry down by one, and the board only changes when something
 * actually expires. So the number of turns waiting has to buy is the soonest expiry on the board,
 * and there is no point starting unless the remaining budget covers it - otherwise the run spends
 * every turn it has and still ends up taking the same bad ad, just poorer.
 *
 * <p>Measured against real play this fires almost never: at one life the survival floor still
 * admits the two most common labels, so a board with nothing above it is roughly a one-in-two-
 * hundred-and-fifty event. It is kept because it is one branch and it covers throwing a run away,
 * not because it earns its place on the numbers. The README says so at more length.
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

    /**
     * The reason to wait this turn, or empty if there is not one.
     *
     * <p>Only consulted after the strategy has refused every ad and the shop policy has declined
     * to buy a potion; those two decisions are not repeated here.
     */
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
