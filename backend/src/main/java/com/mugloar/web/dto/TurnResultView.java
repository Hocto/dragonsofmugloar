package com.mugloar.web.dto;

import com.mugloar.application.TurnEvent;

/** The result of a manual move together with the refreshed board, so no second request is needed. */
public record TurnResultView(TurnEvent event, RunView run) {
}
