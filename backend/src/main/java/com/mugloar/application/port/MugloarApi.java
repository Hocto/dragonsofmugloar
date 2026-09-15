package com.mugloar.application.port;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.util.List;

/**
 * Everything the game logic needs from Mugloar, in domain terms; the adapter handles HTTP, DTOs,
 * retries and decoding. {@code solve} and {@code buy} take the current {@link GameState} because
 * neither response is complete: solve omits the dragon level, buy omits score and high score, and
 * the adapter carries those fields across.
 */
public interface MugloarApi {

    GameState startGame();

    /** Decoded and mapped onto the risk scale. */
    List<Ad> messages(String gameId);

    List<ShopItem> shop(String gameId);

    SolveResult solve(GameState current, String adId);

    PurchaseResult buy(GameState current, String itemId);

    Reputation investigateReputation(String gameId);
}
