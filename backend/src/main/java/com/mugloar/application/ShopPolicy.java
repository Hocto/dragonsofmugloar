package com.mugloar.application;

import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether to spend gold this turn, and on what.
 *
 * <p>The ordering below is the whole policy, and it is deliberate:
 *
 * <ol>
 *   <li><b>A potion when lives are low.</b> Lives are the only resource you cannot earn back by
 *       playing well - the score stops the moment they hit zero, and everything banked stays banked.
 *       At 50 gold a potion is also the cheapest thing on the shelf. Buying one at one life is
 *       never wrong; buying one at two lives is usually right, because the alternative is playing
 *       the next few turns from behind the strategy's own survival floor, which is slow.
 *   <li><b>An upgrade once there is surplus.</b> Dragon level lifts the success chance on every
 *       future ad, so an upgrade pays off across the rest of the run rather than once. It is the
 *       compounding move, which is exactly why it goes second and not first - compounding is
 *       worthless if the run ends next turn.
 *   <li><b>Nothing.</b> Buying costs a turn even when it fails, so spending gold with no reason is
 *       strictly worse than solving an ad.
 * </ol>
 *
 * <p>Upgrades are bought cheapest first. Every upgrade in the shop raises the level by one step
 * regardless of price, so 100 gold buys the same progress as 300 - measured, not assumed.
 *
 * <p>No Spring here; it is constructed in {@code StrategyConfiguration}.
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
            // Can't afford healing and lives are low: hoard, don't spend on upgrades.
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
