package com.mugloar;

import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * A scripted Mugloar, so the orchestrator can be driven through a whole game in a unit test.
 *
 * <p>It keeps the parts of the real rules that the orchestrator depends on: solving and buying both
 * consume a turn, a failed solve costs a life, an unaffordable purchase fails but still burns the
 * turn, and reading the board is free. Outcomes are queued rather than random, so a test can say
 * "this attempt fails" and mean it.
 *
 * <p>Expiry is modelled too, because the waiting logic turns on it. Every call that consumes a turn
 * ticks each ad one closer to expiring and drops the ones that reach zero. Waiting adds no new ads,
 * which is the measured behaviour of the real board and the reason waiting is a narrow move rather
 * than a reroll.
 */
public final class FakeMugloarApi implements MugloarApi {

    private final Deque<Boolean> solveOutcomes = new ArrayDeque<>();
    private final List<String> calls = new ArrayList<>();
    private final List<ShopItem> shop;

    private List<Ad> board;
    private GameState state = new GameState("fake-1", 3, 0, 0, 0, 0, 0);
    private Predicate<String> failEveryCallMatching = call -> false;

    public FakeMugloarApi(List<Ad> board, List<ShopItem> shop) {
        this.board = board;
        this.shop = shop;
    }

    /** Queues the results of the next solves, in order. */
    public FakeMugloarApi solvesWillGo(boolean... outcomes) {
        for (boolean outcome : outcomes) {
            solveOutcomes.add(outcome);
        }
        return this;
    }

    public FakeMugloarApi startingWith(GameState initial) {
        this.state = initial;
        return this;
    }

    /** Makes any call whose name matches blow up, for the failure paths. */
    public FakeMugloarApi failing(Predicate<String> matcher) {
        this.failEveryCallMatching = matcher;
        return this;
    }

    public void setBoard(List<Ad> ads) {
        this.board = ads;
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public GameState state() {
        return state;
    }

    @Override
    public GameState startGame() {
        record("startGame");
        return state;
    }

    @Override
    public List<Ad> messages(String gameId) {
        record("messages");
        return board;
    }

    @Override
    public List<ShopItem> shop(String gameId) {
        record("shop");
        return shop;
    }

    @Override
    public SolveResult solve(GameState current, String adId) {
        record("solve:" + adId);
        Ad ad = board.stream()
                .filter(candidate -> candidate.adId().equals(adId))
                .findFirst()
                .orElseThrow(() -> new MugloarApiException("no such ad", 400));

        boolean success = solveOutcomes.isEmpty() || solveOutcomes.poll();
        spendTurn();
        state = new GameState(
                current.gameId(),
                success ? current.lives() : current.lives() - 1,
                success ? current.gold() + ad.reward() : current.gold(),
                current.level(),
                success ? current.score() + ad.reward() : current.score(),
                current.highScore(),
                current.turn() + 1);
        return new SolveResult(success, success ? "Solved it" : "Failed it", state);
    }

    @Override
    public PurchaseResult buy(GameState current, String itemId) {
        record("buy:" + itemId);
        ShopItem item = shop.stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new MugloarApiException("no such item", 400));

        boolean affordable = item.affordableWith(current.gold());
        // The turn is spent whether or not the purchase lands. That is the real behaviour.
        spendTurn();
        state = new GameState(
                current.gameId(),
                affordable && item.isHealingPotion() ? current.lives() + 1 : current.lives(),
                affordable ? current.gold() - item.cost() : current.gold(),
                affordable && item.isUpgrade() ? current.level() + 1 : current.level(),
                current.score(),
                current.highScore(),
                current.turn() + 1);
        return new PurchaseResult(affordable, state);
    }

    @Override
    public Reputation investigateReputation(String gameId) {
        record("investigateReputation");
        spendTurn();
        return Reputation.NEUTRAL;
    }

    /** One turn passes: everything on the board ages, and anything out of time falls off it. */
    private void spendTurn() {
        board = board.stream()
                .map(ad -> new Ad(ad.adId(), ad.message(), ad.reward(), ad.expiresIn() - 1,
                        ad.risk(), ad.encoding()))
                .filter(ad -> ad.expiresIn() > 0)
                .toList();
    }

    private void record(String call) {
        calls.add(call);
        if (failEveryCallMatching.test(call)) {
            throw new MugloarApiException("scripted failure on " + call, 503);
        }
    }
}
