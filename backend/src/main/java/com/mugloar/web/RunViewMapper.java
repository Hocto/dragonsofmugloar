package com.mugloar.web;

import com.mugloar.application.Board;
import com.mugloar.application.ShopDecision;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.ShopItem;
import com.mugloar.web.dto.AdView;
import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.ShopAdviceView;
import com.mugloar.web.dto.ShopItemView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Flattens the domain into the shapes the browser renders; the frontend receives scores and flags, not rules. */
@Component
public class RunViewMapper {

    /** Excludes UNKNOWN, which is a fallback rather than a step on the scale. */
    private static final int DIFFICULTY_STEPS = RiskLevel.values().length - 1;

    private static final int MAX_EVENTS_IN_VIEW = 60;

    public RunView toView(Run run, Board board, int waitTurnsRemaining) {
        Map<String, AdValuation> scored = board.ranked().stream()
                .collect(Collectors.toMap(v -> v.ad().adId(), Function.identity(), (a, b) -> a));
        String bestAdId = board.ranked().stream().findFirst().map(v -> v.ad().adId()).orElse(null);
        String recommendedItemId = board.shopRecommendation() instanceof ShopDecision.Buy buy
                ? buy.item().id()
                : null;

        List<AdView> ads = board.ads().stream()
                .map(ad -> toAdView(ad, scored.get(ad.adId()), ad.adId().equals(bestAdId)))
                .toList();

        List<ShopItemView> shop = board.shop().stream()
                .map(item -> toShopItemView(item, run, recommendedItemId))
                .toList();

        List<com.mugloar.application.TurnEvent> events = run.events();
        if (events.size() > MAX_EVENTS_IN_VIEW) {
            events = events.subList(events.size() - MAX_EVENTS_IN_VIEW, events.size());
        }

        return new RunView(
                run.id(),
                run.mode().name(),
                run.status().name(),
                run.strategy(),
                run.state(),
                run.reputation(),
                run.failure(),
                ads,
                shop,
                toAdvice(board.shopRecommendation()),
                events,
                run.summary(),
                waitTurnsRemaining);
    }

    private AdView toAdView(Ad ad, AdValuation valuation, boolean recommended) {
        return new AdView(
                ad.adId(),
                ad.message(),
                ad.reward(),
                ad.expiresIn(),
                ad.risk().isKnown() ? ad.risk().label() : "Unknown",
                ad.risk().ordinal() + 1,
                DIFFICULTY_STEPS,
                ad.encoding() != AdEncoding.NONE,
                ad.encoding().name(),
                Optional.ofNullable(valuation).map(AdValuation::successChance).orElse(null),
                Optional.ofNullable(valuation).map(AdValuation::score).orElse(null),
                recommended,
                valuation == null);
    }

    private ShopItemView toShopItemView(ShopItem item, Run run, String recommendedItemId) {
        return new ShopItemView(
                item.id(),
                item.name(),
                item.cost(),
                item.affordableWith(run.state().gold()),
                item.isHealingPotion(),
                item.id().equals(recommendedItemId));
    }

    private ShopAdviceView toAdvice(ShopDecision decision) {
        return switch (decision) {
            case ShopDecision.Buy buy -> new ShopAdviceView("BUY", buy.item().id(), buy.reason());
            case ShopDecision.Skip skip -> new ShopAdviceView("SKIP", null, skip.reason());
        };
    }
}
