package com.mugloar.web.dto;

/**
 * Counts over the run's whole history, computed on the server.
 *
 * <p>The event list the browser receives is capped, and the stream feed it keeps is capped again,
 * so a long run or one resumed after a refresh would under-report itself if the game-over screen
 * counted what it happened to hold. These come from the full list.
 */
public record RunSummary(int solved, int failed, int bought, int idled) {
}
