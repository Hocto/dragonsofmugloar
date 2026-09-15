package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code level} is absent and carries over from the previous state. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolveResponse(
        boolean success, int lives, int gold, int score, int highScore, int turn, String message) {
}
