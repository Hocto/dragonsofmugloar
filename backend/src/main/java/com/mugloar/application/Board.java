package com.mugloar.application;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.ShopItem;
import java.util.List;

/**
 * Everything visible at one moment: the ads, the shop, and the strategy's ranking of the ads.
 * {@code ranked} omits any ad the strategy refuses to attempt; the UI marks those as skipped.
 */
public record Board(
        List<Ad> ads,
        List<ShopItem> shop,
        List<AdValuation> ranked,
        ShopDecision shopRecommendation) {
}
