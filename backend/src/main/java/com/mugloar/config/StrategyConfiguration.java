package com.mugloar.config;

import com.mugloar.application.GameMemories;
import com.mugloar.application.GameOrchestrator;
import com.mugloar.application.ShopPolicy;
import com.mugloar.application.WaitingPolicy;
import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.strategy.AdSelectionStrategy;
import com.mugloar.application.strategy.ExpectedValueStrategy;
import com.mugloar.application.strategy.RewardPerRiskStrategy;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application classes are plain Java; they are wired here. */
@Configuration
public class StrategyConfiguration {

    /** Both strategies are constructed; {@code mugloar.strategy.name} selects one. */
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
    WaitingPolicy waitingPolicy(StrategyProperties properties) {
        return new WaitingPolicy(properties.maxIdleTurns());
    }

    /** One store shared by the web layer, the auto player and the benchmark. */
    @Bean
    GameMemories gameMemories() {
        return new GameMemories();
    }

    @Bean
    GameOrchestrator gameOrchestrator(
            MugloarApi api,
            AdSelectionStrategy strategy,
            ShopPolicy shopPolicy,
            WaitingPolicy waitingPolicy,
            GameMemories memories) {
        return new GameOrchestrator(api, strategy, shopPolicy, waitingPolicy, memories);
    }
}
