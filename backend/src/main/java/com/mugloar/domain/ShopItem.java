package com.mugloar.domain;

/** One row of the shop listing. */
public record ShopItem(String id, String name, int cost) {

    /** The only item that restores a life; everything else is a permanent dragon upgrade. */
    public static final String HEALING_POTION_ID = "hpot";

    public boolean isHealingPotion() {
        return HEALING_POTION_ID.equals(id);
    }

    public boolean isUpgrade() {
        return !isHealingPotion();
    }

    public boolean affordableWith(int gold) {
        return gold >= cost;
    }
}
