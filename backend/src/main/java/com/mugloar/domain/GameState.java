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

    /** Advances the turn locally; the reputation endpoint consumes a turn but reports no state. */
    public GameState advanceTurn() {
        return new GameState(gameId, lives, gold, level, score, highScore, turn + 1);
    }
}
