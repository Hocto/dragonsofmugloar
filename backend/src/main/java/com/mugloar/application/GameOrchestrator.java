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
 * Plays the game, one turn at a time.
 *
 * <p>Turn-at-a-time rather than a {@code while (alive)} loop on purpose: the same object then drives
 * the auto runs, the benchmark and the manual mode, and the web layer decides how fast to call it.
 *
 * <p>This class is the choreography and nothing else. What to attempt is {@link AdSelectionStrategy},
 * what to buy is {@link ShopPolicy}, whether to wait is {@link WaitingPolicy}, and what is
 * remembered between turns lives in {@link GameMemories}. Each of those is testable alone; this
 * one is tested by driving whole games through it against a scripted API.
 *
 * <p>No Spring annotations - it is wired in {@code StrategyConfiguration}.
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

    /**
     * The board has to be fresh every turn. The shop listing does not - the same eleven items at
     * the same prices come back every turn of every game - so it is fetched once and remembered.
     * Both reads are free in game terms but not in practice; Mugloar rate limits per IP.
     */
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
     * One automatic turn from a board the caller already has: shop if the policy says so, attempt
     * the best-scoring ad if there is one, wait if waiting can help, and otherwise take the least
     * bad ad on the board.
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

        Optional<String> reasonToWait =
                waitingPolicy.reasonToWait(state, board, memories.of(state.gameId()));
        if (reasonToWait.isPresent()) {
            return idle(state, sequence, reasonToWait.get());
        }

        return lastResort(board.ads(), state)
                .map(fallback -> solve(state, fallback, sequence))
                .orElseGet(() -> TurnEvent.failed(sequence, state, "Message board was empty"));
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

    /**
     * Manual mode: the human chose to give up the turn rather than attempt anything.
     *
     * <p>Deliberately not subject to {@link WaitingPolicy}. The budget exists to stop an automatic
     * run looping on a board that never improves; a person clicking the button has already decided.
     */
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

    /**
     * Spends the turn on a reputation read, which is the only call that costs a turn and risks
     * nothing. The reason differs by who decided: the strategy explains itself, a person does not
     * have to.
     */
    private TurnEvent idle(GameState before, long sequence, String reason) {
        Reputation reputation = api.investigateReputation(before.gameId());
        GameMemory memory = memories.update(before.gameId(), m -> m.afterIdling(reputation));

        // The reputation call reports no state of its own, so the turn is advanced here.
        GameState after = before.advanceTurn();
        String why = "%s - waited a turn (people %.1f, state %.1f, underworld %.1f)".formatted(
                reason, reputation.people(), reputation.state(), reputation.underworld());

        log.info("turn.idle gameId={} turn={} lives={} gold={} idlesUsed={}/{} reputation={}",
                after.gameId(), after.turn(), after.lives(), after.gold(),
                memory.idlesUsed(), waitingPolicy.budget(), reputation);

        return TurnEvent.idled(sequence, why, before, after);
    }

    /**
     * The strategy dropped everything and waiting cannot help. There is no way to skip a turn, so
     * take the single safest ad and hope. Losing here is better than the alternative, which is
     * not moving at all.
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
