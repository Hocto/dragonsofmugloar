package com.mugloar.domain;

/** What came back from a solve attempt, plus the resulting state. */
public record SolveResult(boolean success, String message, GameState state) {
}
