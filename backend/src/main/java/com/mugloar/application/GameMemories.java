package com.mugloar.application;

import com.mugloar.domain.Reputation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Per-game memory: the shop listing, the waiting budget spent, the last reputation read.
 *
 * <p>Bounded by lifecycle, not by a number. Every path that puts an entry here also removes it:
 * the auto player and the benchmark forget in a {@code finally}, a manual run forgets when it
 * ends, and the run registry forgets when it evicts an abandoned run. So the store can hold at
 * most one entry per run the registry still knows about plus one per benchmark game in flight,
 * and both of those are already capped elsewhere.
 *
 * <p>An earlier version had a size limit of five hundred with random eviction on top of this. It
 * was a guess, and worse, it was papering over the one real leak - abandoned manual runs, which
 * were evicted from the registry without their memory going with them. Fixing the lifecycle made
 * the number redundant, so it is gone; a bound nobody can derive is not a bound.
 *
 * <p>Synchronized rather than concurrent because the critical sections are one map operation long
 * and the callers are a few dozen virtual threads blocked on HTTP most of the time.
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

    /** The game is over, or nobody is coming back for it. Whoever created the entry removes it. */
    public synchronized void forget(String gameId) {
        byGame.remove(gameId);
    }

    public synchronized int size() {
        return byGame.size();
    }
}
