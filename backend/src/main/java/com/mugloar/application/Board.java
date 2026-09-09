package com.mugloar.application;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.ShopItem;
import java.util.List;

/**
 * Everything visible at one moment: the ads, the shop, and what the strategy makes of them.
 *
 * <p>{@code ranked} is the strategy's opinion and is deliberately allowed to be shorter than
 * {@code ads} - anything it refuses to attempt is simply absent, and the UI marks those ads as
 * skipped.
 */
public record Board(
        List<Ad> ads,
        List<ShopItem> shop,
        List<AdValuation> ranked,
        ShopDecision shopRecommendation) {
}
