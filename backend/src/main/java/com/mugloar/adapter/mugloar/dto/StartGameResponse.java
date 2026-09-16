package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StartGameResponse(
        String gameId, int lives, int gold, int level, int score, int highScore, int turn) {
}
