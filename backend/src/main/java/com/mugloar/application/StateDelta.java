package com.mugloar.application;

import com.mugloar.domain.GameState;

/** What one turn changed, shown next to the result so the player does not have to diff two HUDs. */
public record StateDelta(int lives, int gold, int score, int level, int turn) {

    public static final StateDelta NONE = new StateDelta(0, 0, 0, 0, 0);

    public static StateDelta between(GameState before, GameState after) {
        return new StateDelta(
                after.lives() - before.lives(),
                after.gold() - before.gold(),
                after.score() - before.score(),
                after.level() - before.level(),
                after.turn() - before.turn());
    }
}
