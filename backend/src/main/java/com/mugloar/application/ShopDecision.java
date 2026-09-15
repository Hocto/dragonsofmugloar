package com.mugloar.application;

import com.mugloar.domain.ShopItem;

/** What the shop policy wants to do this turn. Sealed so switches over it are exhaustive. */
public sealed interface ShopDecision {

    /** The sentence explaining the decision, shown in the UI and the logs. */
    String reason();

    record Buy(ShopItem item, String reason) implements ShopDecision {
    }

    record Skip(String reason) implements ShopDecision {
    }
}
