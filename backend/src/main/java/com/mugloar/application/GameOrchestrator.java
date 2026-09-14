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
    private static final int MEMORY_LIMIT = 500;

    private final MugloarApi api;
    private final AdSelectionStrategy strategy;
    private final ShopPolicy shopPolicy;
    private final int maxIdleTurns;
    private final Map<String, GameMemory> memoryByGame = new ConcurrentHashMap<>();

    /**
     * What the orchestrator remembers between turns of one game: the shop listing, which never
     * changes; how much of the waiting budget has been spent; and the last reputation reading,
     * which only exists because waiting is what fetches it.
     */
    private record GameMemory(List<ShopItem> shop, int idlesUsed, Reputation reputation) {

        static final GameMemory EMPTY = new GameMemory(null, 0, null);

        GameMemory withShop(List<ShopItem> listing) {
            return new GameMemory(listing, idlesUsed, reputation);
        }

        GameMemory afterIdling(Reputation latest) {
            return new GameMemory(shop, idlesUsed + 1, latest);
        }
    }

    public GameOrchestrator(
            MugloarApi api, AdSelectionStrategy strategy, ShopPolicy shopPolicy, int maxIdleTurns) {
        this.api = api;
        this.strategy = strategy;
        this.shopPolicy = shopPolicy;
        this.maxIdleTurns = maxIdleTurns;
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
        GameMemory memory = memoryByGame.get(gameId);
        if (memory != null && memory.shop() != null) {
            return memory.shop();
        }
        if (memoryByGame.size() >= MEMORY_LIMIT) {
            memoryByGame.keySet().stream().findFirst().ifPresent(memoryByGame::remove);
        }
        List<ShopItem> fetched = api.shop(gameId);
        memoryByGame.merge(gameId, GameMemory.EMPTY.withShop(fetched),
                (existing, fresh) -> existing.withShop(fetched));
        return fetched;
    }

    /** The last reputation read while waiting out a bad board, if the game has ever had to. */
    public Optional<Reputation> reputationFor(String gameId) {
        return Optional.ofNullable(memoryByGame.get(gameId)).map(GameMemory::reputation);
    }

    /** Called when a run ends so a long-running server does not hold this forever. */
    public void forget(String gameId) {
        memoryByGame.remove(gameId);
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

        Optional<AdValuation> choice = board.ranked().stream().findFirst();
        if (choice.isPresent()) {
            return solve(state, choice.get(), sequence);
        }

        // Nothing on the board clears the survival floor, and the shop policy has already decided
        // it cannot fix that with a potion. Waiting is the remaining move.
        if (idlingIsAvailable(state.gameId())) {
            return idle(state, sequence, "Nothing worth attempting at %d %s"
                    .formatted(state.lives(), state.lives() == 1 ? "life" : "lives"));
        }

        return lastResort(board.ads(), state)
                .map(fallback -> solve(state, fallback, sequence))
                .orElseGet(() -> TurnEvent.failed(sequence, state, "Message board was empty"));
    }

    /**
     * Spends the turn without attempting anything.
     *
     * <p>There is no "pass" in this API, but a turn can still be given up: asking for the player's
     * reputation costs one and risks nothing. When the whole board is below the survival floor and
     * there is no gold for a potion, that is a better trade than a coin flip - a lost life ends the
     * run and everything it would still have earned, while a lost turn costs one turn. The board
     * moves on either way, because expiry ticks down and new ads appear.
     *
     * <p>It is budgeted rather than unlimited. A run that waits forever on a board that never
     * improves has simply found a slower way to score nothing.
     */
    private TurnEvent idle(GameState before, long sequence, String reason) {
        Reputation reputation = api.investigateReputation(before.gameId());
        GameMemory memory = memoryByGame
                .merge(before.gameId(), GameMemory.EMPTY.afterIdling(reputation),
                        (existing, fresh) -> existing.afterIdling(reputation));

        // The reputation call reports no state of its own, so the turn is advanced here.
        GameState after = before.advanceTurn();
        // The reason differs by who decided: the strategy explains itself, a person does not have to.
        String why = "%s - waited a turn (people %.1f, state %.1f, underworld %.1f)".formatted(
                reason, reputation.people(), reputation.state(), reputation.underworld());

        log.info("turn.idle gameId={} turn={} lives={} gold={} idlesUsed={}/{} reputation={}",
                after.gameId(), after.turn(), after.lives(), after.gold(),
                memory.idlesUsed(), maxIdleTurns, reputation);

        return TurnEvent.idled(sequence, why, before, after);
    }

    private boolean idlingIsAvailable(String gameId) {
        if (maxIdleTurns <= 0) {
            return false;
        }
        GameMemory memory = memoryByGame.get(gameId);
        return memory == null || memory.idlesUsed() < maxIdleTurns;
    }

    /** Manual mode: the human picked an ad, so no strategy filtering applies. */
    public TurnEvent solveById(GameState state, Board board, String adId, long sequence) {
        Ad ad = board.ads().stream()
                .filter(candidate -> candidate.adId().equals(adId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such ad on the board: " + adId));
        return solve(state, valuate(ad), sequence);
    }

    /**
     * Manual mode: the human chose to give up the turn rather than attempt anything.
     *
     * <p>Deliberately not subject to the waiting budget. The budget exists to stop an automatic run
     * looping on a board that never improves; a person clicking the button has already decided.
     */
    public TurnEvent waitOutTurn(GameState state, long sequence) {
        return idle(state, sequence, "Let the turn pass");
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
