package com.mugloar.web.dto;

import com.mugloar.application.TurnEvent;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import java.util.List;

/** One GET, everything the game screen needs. */
public record RunView(
        String runId,
        String mode,
        String status,
        String strategy,
        GameState state,
        Reputation reputation,
        String failure,
        List<AdView> ads,
        List<ShopItemView> shop,
        ShopAdviceView shopAdvice,
        /** The most recent events only; {@code summary} counts over all of them. */
        List<TurnEvent> events,
        RunSummary summary) {
}
