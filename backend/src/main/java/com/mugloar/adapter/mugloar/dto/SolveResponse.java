package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Note the absent {@code level}: the caller has to carry it over from the previous state. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolveResponse(
        boolean success, int lives, int gold, int score, int highScore, int turn, String message) {
}
