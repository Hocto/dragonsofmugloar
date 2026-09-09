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
}
