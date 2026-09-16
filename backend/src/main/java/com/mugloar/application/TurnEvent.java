package com.mugloar.application;

import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;
import java.time.Instant;

/**
 * One turn, flattened for the SSE stream and the log. Carries the reasoning as well as the result:
 * which ad, its estimated chance, and what it was worth.
 */
public record TurnEvent(
        long sequence,
        TurnAction action,
        String target,
        String description,
        boolean success,
        String apiMessage,
        Double successChance,
        RiskLevel risk,
        Integer reward,
        GameState state,
        StateDelta delta,
        Instant at) {

    public static TurnEvent started(GameState state) {
        return new TurnEvent(0, TurnAction.STARTED, state.gameId(), "Run started", true,
                null, null, null, null, state, StateDelta.NONE, Instant.now());
    }

    public static TurnEvent finished(long sequence, GameState state, String why) {
        return new TurnEvent(sequence, TurnAction.FINISHED, state.gameId(), why, true,
                null, null, null, null, state, StateDelta.NONE, Instant.now());
    }

    public static TurnEvent failed(long sequence, GameState state, String why) {
        return new TurnEvent(sequence, TurnAction.FAILED, state.gameId(), why, false,
                null, null, null, null, state, StateDelta.NONE, Instant.now());
    }

    public static TurnEvent solved(
            long sequence, AdValuation choice, boolean success, String apiMessage,
            GameState before, GameState after) {
        return new TurnEvent(
                sequence,
                TurnAction.SOLVED,
                choice.ad().adId(),
                choice.ad().message(),
                success,
                apiMessage,
                choice.successChance(),
                choice.ad().risk(),
                choice.ad().reward(),
                after,
                StateDelta.between(before, after),
                Instant.now());
    }

    public static TurnEvent idled(long sequence, String why, GameState before, GameState after) {
        return new TurnEvent(sequence, TurnAction.IDLED, before.gameId(), why, true,
                null, null, null, null, after, StateDelta.between(before, after), Instant.now());
    }

    public static TurnEvent bought(
            long sequence, ShopDecision.Buy buy, boolean success, GameState before, GameState after) {
        return new TurnEvent(
                sequence,
                TurnAction.BOUGHT,
                buy.item().id(),
                buy.reason(),
                success,
                null,
                null,
                null,
                buy.item().cost(),
                after,
                StateDelta.between(before, after),
                Instant.now());
    }

    public boolean endsRun() {
        return action == TurnAction.FINISHED || action == TurnAction.FAILED;
    }
}
