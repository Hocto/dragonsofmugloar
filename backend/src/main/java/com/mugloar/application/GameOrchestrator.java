package com.mugloar.application;

import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.strategy.AdSelectionStrategy;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays the game, one turn at a time.
 *
 * <p>Turn-at-a-time rather than a {@code while (alive)} loop on purpose: the same object then drives
 * the auto runs, the benchmark and the manual mode, and the web layer decides how fast to call it.
 *
 * <p>Only {@code solve} and {@code buy} consume a turn, but reading is not free in practice -
 * Mugloar rate limits per IP, and fetching the board and the shop every turn was enough to trip it.
 * The board has to be fresh. The shop listing does not: the same eleven items at the same prices
 * come back every turn of every game, so it is fetched once per game and remembered.
 *
 * <p>No Spring annotations - it is wired in {@code StrategyConfiguration}.
 */
public final class GameOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GameOrchestrator.class);

    /**
     * Bounded so a long-lived server cannot accumulate one entry per game ever played. Games are
     * evicted oldest-first by insertion order, which is close enough for a listing that is
     * identical everywhere anyway.
     */
    private static final int SHOP_CACHE_LIMIT = 500;

    private final MugloarApi api;
    private final AdSelectionStrategy strategy;
    private final ShopPolicy shopPolicy;
    private final Map<String, List<ShopItem>> shopByGame = new ConcurrentHashMap<>();

    public GameOrchestrator(MugloarApi api, AdSelectionStrategy strategy, ShopPolicy shopPolicy) {
        this.api = api;
        this.strategy = strategy;
        this.shopPolicy = shopPolicy;
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
        List<ShopItem> shop = shopFor(state.gameId());
        return new Board(ads, shop, strategy.rank(ads, state), shopPolicy.decide(state, shop));
    }

    private List<ShopItem> shopFor(String gameId) {
        List<ShopItem> cached = shopByGame.get(gameId);
        if (cached != null) {
            return cached;
        }
        if (shopByGame.size() >= SHOP_CACHE_LIMIT) {
            shopByGame.keySet().stream().findFirst().ifPresent(shopByGame::remove);
        }
        List<ShopItem> fetched = api.shop(gameId);
        shopByGame.put(gameId, fetched);
        return fetched;
    }

    /** Called when a run ends so a long-running server does not hold shop listings forever. */
    public void forget(String gameId) {
        shopByGame.remove(gameId);
    }

    /** Fetches a board and plays a turn from it. The benchmark's entry point. */
    public TurnEvent playTurn(GameState state, long sequence) {
        if (state.isOver()) {
            return TurnEvent.finished(sequence, state, "No lives left");
        }
        return playTurn(state, board(state), sequence);
    }

    /**
     * One automatic turn from a board the caller already has: shop if the policy says so, otherwise
     * attempt the best-scoring ad.
     *
     * <p>Taking the board as an argument rather than fetching it is what stops the web layer from
     * reading the message board twice per turn - once to play and once to render.
     */
    public TurnEvent playTurn(GameState state, Board board, long sequence) {
        if (state.isOver()) {
            return TurnEvent.finished(sequence, state, "No lives left");
        }

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
    public TurnEvent solveById(GameState state, Board board, String adId, long sequence) {
        Ad ad = board.ads().stream()
                .filter(candidate -> candidate.adId().equals(adId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such ad on the board: " + adId));
        return solve(state, valuate(ad), sequence);
    }

    /** Manual mode: the human picked an item. */
    public TurnEvent buyById(GameState state, Board board, String itemId, long sequence) {
        ShopItem item = board.shop().stream()
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
                .map(GameOrchestrator::valuate);
    }

    /** For a hand-picked ad, or a last-resort one, there is no ranking to look the numbers up in. */
    private static AdValuation valuate(Ad ad) {
        double chance = ad.risk().successRate();
        return new AdValuation(ad, chance, ad.reward() * chance, 1.0, ad.reward() * chance);
    }

    private static String round(double value) {
        return String.format("%.3f", value);
    }
}
