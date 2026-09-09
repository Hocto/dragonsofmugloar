package com.mugloar.application.strategy;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.SuccessModel;
import java.util.Comparator;
import java.util.List;

/**
 * The first thing I wrote: reward divided by risk.
 *
 * <p>It is kept because it is a fair baseline and because the benchmark numbers only mean something
 * next to something else. It loses for a specific reason - it has no concept of time, so it happily
 * leaves a 150 gold ad sitting on the board while it clears cheap safe ones, and the good ad expires.
 * {@link ExpectedValueStrategy} is the same idea with expiry and dragon level folded in.
 */
public final class RewardPerRiskStrategy implements AdSelectionStrategy {

    public static final String NAME = "reward-per-risk";

    private final SuccessModel successModel;

    public RewardPerRiskStrategy(SuccessModel successModel) {
        this.successModel = successModel;
    }

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
                    double chance = successModel.probability(ad, state);
                    return new AdValuation(ad, chance, ad.reward() * chance, 1.0, score);
                })
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
