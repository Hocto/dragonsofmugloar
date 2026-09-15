package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks ads by reward divided by risk rank. Kept as a baseline for the benchmark. It has no notion
 * of expiry and no survival floor.
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
