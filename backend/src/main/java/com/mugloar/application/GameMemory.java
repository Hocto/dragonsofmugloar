package com.mugloar.application;

import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import java.util.List;

/**
 * What is remembered between turns of one game: the shop listing (constant for the game, and a
 * rate-limited request to refetch), the waiting budget spent, and the last reputation read.
 * Immutable; every change is a new value.
 */
public record GameMemory(List<ShopItem> shop, int idlesUsed, Reputation reputation) {

    public static final GameMemory EMPTY = new GameMemory(null, 0, null);

    public GameMemory withShop(List<ShopItem> listing) {
        return new GameMemory(listing, idlesUsed, reputation);
    }

    public GameMemory afterIdling(Reputation latest) {
        return new GameMemory(shop, idlesUsed + 1, latest);
    }

    public boolean knowsShop() {
        return shop != null;
    }
}
