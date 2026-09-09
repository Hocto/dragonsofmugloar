package com.mugloar.application;

import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;
import java.time.Instant;

/**
 * One turn, flattened into something that serialises straight onto the SSE stream and reads well in
 * a log line.
 *
 * <p>It carries the reasoning, not just the result: which ad, how likely the strategy thought it
 * was, what it was worth. That is the difference between a UI that shows a bot playing and one that
 * shows why the bot played that way.
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
