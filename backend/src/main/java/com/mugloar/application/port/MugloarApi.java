package com.mugloar.application.port;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.util.List;

/**
 * Everything the game logic needs from Mugloar, in domain terms.
 *
 * <p>The adapter behind this deals with HTTP, DTOs, retries and decoding. Above this line none of
 * that exists, which is what makes the orchestrator and the strategies testable without a server.
 *
 * <p>{@code solve} and {@code buy} take the current {@link GameState} rather than a game id because
 * neither response is complete: solve omits the dragon level, buy omits score and high score. The
 * adapter carries the missing fields across so callers always get a whole state back.
 */
public interface MugloarApi {

    GameState startGame();

    /** Decoded and risk-mapped. Callers never see Base64 or ROT13. */
    List<Ad> messages(String gameId);

    List<ShopItem> shop(String gameId);

    SolveResult solve(GameState current, String adId);

    PurchaseResult buy(GameState current, String itemId);

    Reputation investigateReputation(String gameId);
}
