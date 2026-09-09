package com.mugloar.application;

import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.ShopItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShopPolicyTest {

    private static final ShopItem POTION = new ShopItem("hpot", "Healing potion", 50);
    private static final ShopItem CHEAP_UPGRADE = new ShopItem("cs", "Claw Sharpening", 100);
    private static final ShopItem PRICEY_UPGRADE = new ShopItem("ch", "Claw Honing", 300);
    private static final List<ShopItem> SHOP = List.of(POTION, CHEAP_UPGRADE, PRICEY_UPGRADE);

    private final ShopPolicy policy = new ShopPolicy(2, 50);

    @Test
    void healsBeforeItUpgrades() {
        ShopDecision decision = policy.decide(state(2, 500, 0), SHOP);

        assertThat(decision).isInstanceOfSatisfying(ShopDecision.Buy.class,
                buy -> assertThat(buy.item()).isEqualTo(POTION));
    }

    @Test
    void hoardsGoldWhenLivesAreLowAndAPotionIsOutOfReach() {
        ShopDecision decision = policy.decide(state(1, 40, 0), SHOP);

        assertThat(decision).isInstanceOf(ShopDecision.Skip.class);
        assertThat(decision.reason()).contains("saving for a potion");
    }

    @Test
    void buysAnUpgradeOnceLivesAreComfortableAndGoldIsSpare() {
        ShopDecision decision = policy.decide(state(4, 300, 0), SHOP);

        assertThat(decision).isInstanceOfSatisfying(ShopDecision.Buy.class,
                buy -> assertThat(buy.item()).isEqualTo(CHEAP_UPGRADE));
    }

    @Test
    void picksTheCheapestUpgradeBecauseTheyAllGiveTheSameLevel() {
        ShopDecision decision = policy.decide(state(5, 2000, 0), SHOP);

        assertThat(decision).isInstanceOfSatisfying(ShopDecision.Buy.class,
                buy -> assertThat(buy.item()).isEqualTo(CHEAP_UPGRADE));
    }

    @Test
    void keepsPotionMoneyBackWhenBuyingAnUpgrade() {
        // 140 gold covers the 100 upgrade but would leave 40, which is not a potion.
        assertThat(policy.decide(state(4, 140, 0), SHOP)).isInstanceOf(ShopDecision.Skip.class);
        assertThat(policy.decide(state(4, 150, 0), SHOP)).isInstanceOf(ShopDecision.Buy.class);
    }

    @Test
    void doesNothingWhenItCannotAffordAnything() {
        ShopDecision decision = policy.decide(state(4, 10, 0), SHOP);

        assertThat(decision).isInstanceOf(ShopDecision.Skip.class);
        assertThat(decision.reason()).contains("10 gold");
    }

    @Test
    void copesWithSomeoneRemovingThePotionFromTheShop() {
        List<ShopItem> noPotions = List.of(CHEAP_UPGRADE, PRICEY_UPGRADE);

        // Falls back to the configured reserve instead of assuming a potion price.
        assertThat(policy.decide(state(4, 149, 0), noPotions)).isInstanceOf(ShopDecision.Skip.class);
        assertThat(policy.decide(state(4, 151, 0), noPotions)).isInstanceOfSatisfying(
                ShopDecision.Buy.class, buy -> assertThat(buy.item()).isEqualTo(CHEAP_UPGRADE));
    }

    @Test
    void neverBuysSomethingItCannotPayFor() {
        // A failed purchase still burns a turn, so an unaffordable Buy would be a real bug.
        for (int gold = 0; gold <= 400; gold += 7) {
            ShopDecision decision = policy.decide(state(4, gold, 0), SHOP);
            if (decision instanceof ShopDecision.Buy buy) {
                assertThat(buy.item().cost())
                        .as("policy suggested %s with %d gold", buy.item().id(), gold)
                        .isLessThanOrEqualTo(gold);
            }
        }
    }
}
