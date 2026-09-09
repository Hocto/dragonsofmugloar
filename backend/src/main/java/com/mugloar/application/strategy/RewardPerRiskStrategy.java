package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.Comparator;
import java.util.List;

/**
 * The first thing I wrote: reward divided by risk.
 *
 * <p>It is kept because a benchmark number only means something next to another one. It loses for
 * two specific reasons. It has no concept of time, so it leaves a 250 gold ad on the board while it
 * clears cheap safe ones and the good ad expires. And it has no floor, so a big enough reward will
 * talk it into a "Suicide mission" on its last life.
 */
public final class RewardPerRiskStrategy implements AdSelectionStrategy {

    public static final String NAME = "reward-per-risk";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<AdValuation> rank(List<Ad> ads, GameState state) {
        return ads.stream()
                .filter(ad -> ad.risk().isKnown())
                .map(ad -> {
                    // +1 so the safest label divides by one rather than by zero.
                    double riskWeight = ad.risk().ordinal() + 1.0;
                    double score = ad.reward() / riskWeight;
                    double chance = ad.risk().successRate();
                    return new AdValuation(ad, chance, ad.reward() * chance, 1.0, score);
                })
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
