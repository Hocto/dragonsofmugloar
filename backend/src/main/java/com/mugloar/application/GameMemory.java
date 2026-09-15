package com.mugloar.application;

import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import java.util.List;

/**
 * What is remembered between turns of one game.
 *
 * <p>Three things, none of which Mugloar will tell us twice for free: the shop listing, which never
 * changes and costs a rate-limited request to refetch; how much of the waiting budget has been
 * spent; and the last reputation reading, which only exists because waiting is what fetches it.
 *
 * <p>Immutable. Every change is a new value, so the store can swap it in atomically.
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
