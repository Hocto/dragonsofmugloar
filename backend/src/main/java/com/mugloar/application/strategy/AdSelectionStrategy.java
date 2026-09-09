package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.List;
import java.util.Optional;

/**
 * Decides which ad to attempt next.
 *
 * <p>Two implementations ship, picked with {@code mugloar.strategy} in application.yml, so the two
 * can be benchmarked head to head instead of argued about.
 */
public interface AdSelectionStrategy {

    /** Configuration key, e.g. {@code expected-value}. */
    String name();

    /**
     * Every ad, scored and ordered best first. The frontend uses this to explain the board; the
     * orchestrator only needs the head of the list.
     *
     * <p>Ads the strategy refuses to touch are left out entirely.
     */
    List<AdValuation> rank(List<Ad> ads, GameState state);

    /** Empty when nothing on the board is worth attempting. */
    default Optional<AdValuation> select(List<Ad> ads, GameState state) {
        return rank(ads, state).stream().findFirst();
    }
}
