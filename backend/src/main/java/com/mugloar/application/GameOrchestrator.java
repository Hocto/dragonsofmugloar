package com.mugloar.application;

import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.strategy.AdSelectionStrategy;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdValuation;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays the game one turn at a time, so the same object drives auto runs, manual mode and the
 * benchmark and the caller controls pacing. What to attempt is {@link AdSelectionStrategy}, what
 * to buy is {@link ShopPolicy}, whether to wait is {@link WaitingPolicy}, and per-game state lives
 * in {@link GameMemories}.
 */
public final class GameOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GameOrchestrator.class);

    private final MugloarApi api;
    private final AdSelectionStrategy strategy;
    private final ShopPolicy shopPolicy;
    private final WaitingPolicy waitingPolicy;
    private final GameMemories memories;

    public GameOrchestrator(
            MugloarApi api,
            AdSelectionStrategy strategy,
            ShopPolicy shopPolicy,
            WaitingPolicy waitingPolicy,
            GameMemories memories) {
        this.api = api;
        this.strategy = strategy;
        this.shopPolicy = shopPolicy;
        this.waitingPolicy = waitingPolicy;
        this.memories = memories;
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

    /** Fetches the board; the shop listing is constant for a game and is fetched once, since the upstream rate limits per IP. */
    public Board board(GameState state) {
        List<Ad> ads = api.messages(state.gameId());
        List<ShopItem> shop = shopFor(state.gameId());
        return new Board(ads, shop, strategy.rank(ads, state), shopPolicy.decide(state, shop));
    }

    private List<ShopItem> shopFor(String gameId) {
        GameMemory memory = memories.of(gameId);
        if (memory.knowsShop()) {
            return memory.shop();
        }
        List<ShopItem> fetched = api.shop(gameId);
        memories.update(gameId, existing -> existing.withShop(fetched));
        return fetched;
    }

    /** Fetches a board and plays a turn from it. The benchmark's entry point. */
    public TurnEvent playTurn(GameState state, long sequence) {
        if (state.isOver()) {
            return TurnEvent.finished(sequence, state, "No lives left");
        }
        return playTurn(state, board(state), sequence);
    }

    /**
     * One automatic turn from a board the caller already holds: buy if the shop policy says so,
     * else attempt the best-ranked ad, else wait if waiting can help, else take the least bad ad.
     */
    public TurnEvent playTurn(GameState state, Board board, long sequence) {
        if (state.isOver()) {
            return TurnEvent.finished(sequence, state, "No lives left");
        }

        if (board.shopRecommendation() instanceof ShopDecision.Buy buy) {
            return buy(state, buy, sequence);
        }

        Optional<AdValuation> choice = board.ranked().stream().findFirst();
        if (choice.isPresent()) {
            return solve(state, choice.get(), sequence);
        }

        Optional<String> reasonToWait =
                waitingPolicy.reasonToWait(state, board, memories.of(state.gameId()));
        if (reasonToWait.isPresent()) {
            return idle(state, sequence, reasonToWait.get());
        }

        return lastResort(board.ads(), state)
                .map(fallback -> solve(state, fallback, sequence))
                .orElseGet(() -> TurnEvent.failed(sequence, state, "Message board was empty"));
    }

    /** Manual mode: the player picked an ad; no strategy filtering applies. */
    public TurnEvent solveById(GameState state, Board board, String adId, long sequence) {
        Ad ad = board.ads().stream()
                .filter(candidate -> candidate.adId().equals(adId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such ad on the board: " + adId));
        return solve(state, valuate(ad), sequence);
    }

    /** Manual mode: the player picked an item. */
    public TurnEvent buyById(GameState state, Board board, String itemId, long sequence) {
        ShopItem item = board.shop().stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such item in the shop: " + itemId));
        return buy(state, new ShopDecision.Buy(item, "Bought by hand"), sequence);
    }

    /** Manual mode: the player gave up the turn. Not subject to {@link WaitingPolicy}'s budget, which exists to stop an automatic loop. */
    public TurnEvent waitOutTurn(GameState state, long sequence) {
        return idle(state, sequence, "Let the turn pass");
    }

    private TurnEvent solve(GameState before, AdValuation choice, long sequence) {
        SolveResult result = api.solve(before, choice.ad().adId());
        GameState after = result.state();
        log.info("turn.solve gameId={} turn={} adId={} risk={} reward={} chance={} urgency={} "
                        + "expiresIn={} success={} lives={} gold={} score={} level={}",
                after.gameId(), after.turn(), choice.ad().adId(), choice.ad().risk(),
                choice.ad().reward(), round(choice.successChance()), round(choice.urgency()),
                choice.ad().expiresIn(), result.success(), after.lives(), after.gold(),
                after.score(), after.level());
        return TurnEvent.solved(sequence, choice, result.success(), result.message(), before, after);
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

    /** Spends the turn on a reputation read, the only call that costs a turn and risks nothing. */
    private TurnEvent idle(GameState before, long sequence, String reason) {
        Reputation reputation = api.investigateReputation(before.gameId());
        GameMemory memory = memories.update(before.gameId(), m -> m.afterIdling(reputation));

        // The reputation call reports no state, so the turn is advanced locally.
        GameState after = before.advanceTurn();
        String why = "%s - waited a turn (people %.1f, state %.1f, underworld %.1f)".formatted(
                reason, reputation.people(), reputation.state(), reputation.underworld());

        log.info("turn.idle gameId={} turn={} lives={} gold={} idlesUsed={}/{} reputation={}",
                after.gameId(), after.turn(), after.lives(), after.gold(),
                memory.idlesUsed(), waitingPolicy.budget(), reputation);

        return TurnEvent.idled(sequence, why, before, after);
    }

    /** The strategy refused everything and waiting cannot help; the safest ad on the board is the only move left. */
    private Optional<AdValuation> lastResort(List<Ad> ads, GameState state) {
        return ads.stream()
                .filter(ad -> ad.risk().isKnown())
                .min(Comparator.comparingInt(ad -> ad.risk().ordinal()))
                .map(GameOrchestrator::valuate);
    }

    /** Valuation for an ad the strategy did not rank. */
    private static AdValuation valuate(Ad ad) {
        double chance = ad.risk().successRate();
        return new AdValuation(ad, chance, ad.reward() * chance, 1.0, ad.reward() * chance);
    }

    private static String round(double value) {
        return String.format("%.3f", value);
    }
}
