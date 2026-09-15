package com.mugloar.application;

import com.mugloar.domain.Reputation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Per-game memory: the shop listing, the waiting budget spent, and the last reputation read.
 * Bounded by lifecycle rather than by a size limit: every caller that creates an entry also
 * removes it (auto player and benchmark in a {@code finally}, manual runs on completion, the
 * registry on eviction), so the store holds at most one entry per live run.
 */
public final class GameMemories {

    private final Map<String, GameMemory> byGame = new HashMap<>();

    public synchronized GameMemory of(String gameId) {
        return byGame.getOrDefault(gameId, GameMemory.EMPTY);
    }

    /** Applies a change and returns the new value, creating the entry if there was none. */
    public synchronized GameMemory update(String gameId, UnaryOperator<GameMemory> change) {
        GameMemory next = change.apply(byGame.getOrDefault(gameId, GameMemory.EMPTY));
        byGame.put(gameId, next);
        return next;
    }

    public synchronized Optional<Reputation> reputationOf(String gameId) {
        return Optional.ofNullable(byGame.get(gameId)).map(GameMemory::reputation);
    }

    /** Removes the entry; called by whichever component created it. */
    public synchronized void forget(String gameId) {
        byGame.remove(gameId);
    }

    public synchronized int size() {
        return byGame.size();
    }
}
