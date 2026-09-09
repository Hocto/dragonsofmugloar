package com.mugloar.application;

import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.strategy.AdSelectionStrategy;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import com.mugloar.domain.SuccessModel;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays the game, one turn at a time.
 *
 * <p>Turn-at-a-time rather than a {@code while (alive)} loop on purpose: the same object then drives
 * the auto runs, the benchmark and the manual mode, and the web layer decides how fast to call it.
 *
 * <p>Only {@code solve} and {@code buy} consume a turn. Reading the board and the shop is free, so
 * this refetches both every turn instead of trying to keep a cached copy honest.
 *
 * <p>No Spring annotations - it is wired in {@code StrategyConfiguration}.
 */
public final class GameOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GameOrchestrator.class);

    private final MugloarApi api;
    private final AdSelectionStrategy strategy;
    private final ShopPolicy shopPolicy;
    private final SuccessModel successModel;

    public GameOrchestrator(
            MugloarApi api,
            AdSelectionStrategy strategy,
            ShopPolicy shopPolicy,
            SuccessModel successModel) {
        this.api = api;
        this.strategy = strategy;
        this.shopPolicy = shopPolicy;
        this.successModel = successModel;
    }

    public String strategyName() {
        return strategy.name();
    }

    public GameState start() {
        GameState state = api.startGame();
        log.info("run.start gameId={} strategy={} lives={} gold={}",
                state.gameId(), strategy.name(), state.lives(), state.gold());
        return state;
    }

    public Board board(GameState state) {
        List<Ad> ads = api.messages(state.gameId());
        List<ShopItem> shop = api.shop(state.gameId());
        return new Board(ads, shop, strategy.rank(ads, state), shopPolicy.decide(state, shop));
    }

    /** One automatic turn: shop if the policy says so, otherwise attempt the best-scoring ad. */
    public TurnEvent playTurn(GameState state, long sequence) {
        if (state.isOver()) {
            return TurnEvent.finished(sequence, state, "No lives left");
        }
        Board board = board(state);

        if (board.shopRecommendation() instanceof ShopDecision.Buy buy) {
            return buy(state, buy, sequence);
        }

        Optional<AdValuation> choice = board.ranked().stream().findFirst()
                .or(() -> lastResort(board.ads(), state));
        if (choice.isEmpty()) {
            return TurnEvent.failed(sequence, state, "Message board was empty");
        }
        return solve(state, choice.get(), sequence);
    }

    /** Manual mode: the human picked an ad, so no strategy filtering applies. */
    public TurnEvent solveById(GameState state, String adId, long sequence) {
        Ad ad = api.messages(state.gameId()).stream()
                .filter(candidate -> candidate.adId().equals(adId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such ad on the board: " + adId));
        return solve(state, valuate(ad, state), sequence);
    }

    /** Manual mode: the human picked an item. */
    public TurnEvent buyById(GameState state, String itemId, long sequence) {
        ShopItem item = api.shop(state.gameId()).stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such item in the shop: " + itemId));
        return buy(state, new ShopDecision.Buy(item, "Bought by hand"), sequence);
    }

    private TurnEvent solve(GameState before, AdValuation choice, long sequence) {
        SolveResult result = api.solve(before, choice.ad().adId());
        GameState after = result.state();
        TurnEvent event = TurnEvent.solved(
                sequence, choice, result.success(), result.message(), before, after);
        log.info("turn.solve gameId={} turn={} adId={} risk={} reward={} chance={} urgency={} "
                        + "expiresIn={} success={} lives={} gold={} score={} level={}",
                after.gameId(), after.turn(), choice.ad().adId(), choice.ad().risk(),
                choice.ad().reward(), round(choice.successChance()), round(choice.urgency()),
                choice.ad().expiresIn(), result.success(), after.lives(), after.gold(),
                after.score(), after.level());
        return event;
    }

    private TurnEvent buy(GameState before, ShopDecision.Buy buy, long sequence) {
        PurchaseResult result = api.buy(before, buy.item().id());
        GameState after = result.state();
        log.info("turn.buy gameId={} turn={} itemId={} cost={} success={} reason=\"{}\" "
                        + "lives={} gold={} level={}",
                after.gameId(), after.turn(), buy.item().id(), buy.item().cost(),
                result.success(), buy.reason(), after.lives(), after.gold(), after.level());
        return TurnEvent.bought(sequence, buy, result.success(), before, after);
    }

    /**
     * The strategy dropped everything, usually because the survival floor rejected the whole board
     * and there was no gold for a potion. There is no way to pass a turn, so take the single safest
     * ad and hope. Losing here is better than the alternative, which is not moving at all.
     */
    private Optional<AdValuation> lastResort(List<Ad> ads, GameState state) {
        return ads.stream()
                .filter(ad -> ad.risk().isKnown())
                .min(Comparator.comparingInt(ad -> ad.risk().ordinal()))
                .map(ad -> valuate(ad, state));
    }

    private AdValuation valuate(Ad ad, GameState state) {
        double chance = successModel.probability(ad, state);
        return new AdValuation(ad, chance, ad.reward() * chance, 1.0, ad.reward() * chance);
    }

    private static String round(double value) {
        return String.format("%.3f", value);
    }
}
