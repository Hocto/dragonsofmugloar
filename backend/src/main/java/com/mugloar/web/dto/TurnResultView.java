package com.mugloar.web.dto;

import com.mugloar.application.TurnEvent;

/**
 * The answer to a manual solve or buy: what just happened, and the refreshed board, so the UI never
 * has to fire a second request to redraw.
 */
public record TurnResultView(TurnEvent event, RunView run) {
}
