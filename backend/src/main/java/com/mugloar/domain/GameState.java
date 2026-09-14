package com.mugloar.domain;

/** The scoreboard as the API reports it after every action. */
public record GameState(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int highScore,
        int turn) {

    public boolean isOver() {
        return lives <= 0;
    }

    public GameState withLives(int newLives) {
        return new GameState(gameId, newLives, gold, level, score, highScore, turn);
    }

    /**
     * Not every call that costs a turn reports the new state back - the reputation endpoint returns
     * three numbers and nothing else - so the counter has to be advanced here or it silently drifts
     * behind what Mugloar thinks the turn is.
     */
    public GameState advanceTurn() {
        return new GameState(gameId, lives, gold, level, score, highScore, turn + 1);
    }
}
