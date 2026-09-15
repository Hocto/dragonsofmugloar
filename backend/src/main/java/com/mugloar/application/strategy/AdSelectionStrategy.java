package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.List;
import java.util.Optional;

/** Decides which ad to attempt next. Implementations are selected by {@code mugloar.strategy.name}. */
public interface AdSelectionStrategy {

    /** Configuration key, e.g. {@code expected-value}. */
    String name();

    /** Every ad the strategy would attempt, scored and ordered best first; refused ads are omitted. */
    List<AdValuation> rank(List<Ad> ads, GameState state);

    /** Empty when nothing on the board is worth attempting. */
    default Optional<AdValuation> select(List<Ad> ads, GameState state) {
        return rank(ads, state).stream().findFirst();
    }
}
