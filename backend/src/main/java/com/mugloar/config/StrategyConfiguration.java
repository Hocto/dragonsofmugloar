package com.mugloar.config;

import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopPolicy;
import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.strategy.AdSelectionStrategy;
import com.mugloar.application.strategy.ExpectedValueStrategy;
import com.mugloar.application.strategy.RewardPerRiskStrategy;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place that knows both Spring and the game logic.
 *
 * <p>Everything in {@code domain} and {@code application} is plain Java with constructors, so it is
 * assembled here rather than annotated in place. That is what lets the strategy tests instantiate a
 * strategy with {@code new} and no application context at all.
 */
@Configuration
public class StrategyConfiguration {

    /** Both strategies are built here; {@code mugloar.strategy.name} decides which one plays. */
    @Bean
    AdSelectionStrategy adSelectionStrategy(StrategyProperties properties) {
        List<AdSelectionStrategy> available = List.of(
                new ExpectedValueStrategy(properties.urgencyWeight()),
                new RewardPerRiskStrategy());

        return available.stream()
                .filter(strategy -> strategy.name().equals(properties.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown mugloar.strategy.name '%s'. Available: %s".formatted(
                                properties.name(),
                                available.stream().map(AdSelectionStrategy::name).toList())));
    }

    @Bean
    ShopPolicy shopPolicy(StrategyProperties properties) {
        return new ShopPolicy(properties.healingThresholdLives(), properties.upgradeGoldReserve());
    }

    @Bean
    GameOrchestrator gameOrchestrator(
            MugloarApi api,
            AdSelectionStrategy strategy,
            ShopPolicy shopPolicy,
            StrategyProperties properties) {
        return new GameOrchestrator(api, strategy, shopPolicy, properties.maxIdleTurns());
    }
}
