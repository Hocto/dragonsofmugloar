package com.mugloar.application;

import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether to spend gold this turn, and on what, in a fixed order: a potion when lives are
 * at or below the threshold, then the cheapest affordable upgrade while keeping potion money in
 * reserve, otherwise nothing. Lives are the only resource the game does not give back; upgrades
 * come second because dragon level multiplies every subsequent reward (measured: the same label
 * pays roughly four times more at level 6 than at level 2) but is worthless to a run that ends.
 * Every upgrade raises the level by one regardless of price, so cheapest first. A failed purchase
 * still consumes a turn, so nothing unaffordable is ever suggested.
 */
public final class ShopPolicy {

    private final int healingThresholdLives;
    private final int upgradeGoldReserve;

    /**
     * @param healingThresholdLives buy a potion at or below this many lives
     * @param upgradeGoldReserve    gold to keep back after an upgrade, so a potion stays affordable
     */
    public ShopPolicy(int healingThresholdLives, int upgradeGoldReserve) {
        this.healingThresholdLives = healingThresholdLives;
        this.upgradeGoldReserve = upgradeGoldReserve;
    }

    public ShopDecision decide(GameState state, List<ShopItem> shop) {
        Optional<ShopItem> potion = shop.stream().filter(ShopItem::isHealingPotion).findFirst();

        if (state.lives() <= healingThresholdLives) {
            Optional<ShopItem> affordable = potion.filter(p -> p.affordableWith(state.gold()));
            if (affordable.isPresent()) {
                return new ShopDecision.Buy(
                        affordable.get(),
                        "lives at " + state.lives() + ", healing before anything else");
            }
            // Healing is unaffordable and lives are low: keep the gold for a potion.
            return new ShopDecision.Skip(
                    "lives at " + state.lives() + ", saving for a potion (" + state.gold() + " gold)");
        }

        int reserve = potion.map(ShopItem::cost).orElse(upgradeGoldReserve);
        int spendable = state.gold() - reserve;
        Optional<ShopItem> upgrade = shop.stream()
                .filter(ShopItem::isUpgrade)
                .filter(item -> item.cost() <= spendable)
                .min(Comparator.comparingInt(ShopItem::cost));

        return upgrade
                .<ShopDecision>map(item -> new ShopDecision.Buy(
                        item,
                        "surplus gold (" + state.gold() + "), levelling up with " + item.name()))
                .orElseGet(() -> new ShopDecision.Skip(
                        "nothing worth buying at " + state.gold() + " gold"));
    }
}
