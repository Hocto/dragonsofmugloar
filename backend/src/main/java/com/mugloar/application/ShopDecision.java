package com.mugloar.application;

import com.mugloar.domain.ShopItem;

/**
 * What the shop policy wants to do this turn. Sealed so the orchestrator's switch is exhaustive and
 * a third case cannot be added without the compiler pointing at every place that has to handle it.
 */
public sealed interface ShopDecision {

    /** Every decision carries the sentence that explains it; the UI and the logs both show it. */
    String reason();

    record Buy(ShopItem item, String reason) implements ShopDecision {
    }

    record Skip(String reason) implements ShopDecision {
    }
}
