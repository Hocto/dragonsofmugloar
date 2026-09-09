package com.mugloar.adapter.mugloar;

import com.mugloar.adapter.mugloar.dto.MessageResponse;
import com.mugloar.adapter.mugloar.dto.PurchaseResponse;
import com.mugloar.adapter.mugloar.dto.ReputationResponse;
import com.mugloar.adapter.mugloar.dto.ShopItemResponse;
import com.mugloar.adapter.mugloar.dto.SolveResponse;
import com.mugloar.adapter.mugloar.dto.StartGameResponse;
import com.mugloar.application.port.MugloarApi;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Mugloar API over HTTP.
 *
 * <p>Three things this class is responsible for and nothing above it is: turning DTOs into domain
 * types, turning any non-2xx into a {@link MugloarApiException}, and retrying the failures that are
 * worth retrying.
 *
 * <p>Error responses come back as HTML, not JSON, so nothing here tries to parse an error body - it
 * keeps a short snippet for the log and moves on.
 */
public class MugloarRestClient implements MugloarApi {

    private static final int ERROR_SNIPPET_LIMIT = 200;

    private final RestClient http;
    private final AdDecoder decoder;
    private final Backoff backoff;

    public MugloarRestClient(RestClient http, AdDecoder decoder, MugloarProperties properties) {
        this.http = http;
        this.decoder = decoder;
        this.backoff = new Backoff(properties.retry());
    }

    @Override
    public GameState startGame() {
        StartGameResponse response = backoff.call("startGame", () ->
                post("/game/start", StartGameResponse.class));
        return new GameState(
                response.gameId(), response.lives(), response.gold(),
                response.level(), response.score(), response.highScore(), response.turn());
    }

    @Override
    public List<Ad> messages(String gameId) {
        List<MessageResponse> raw = backoff.call("messages", () -> guard(() -> http.get()
                .uri("/{gameId}/messages", gameId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, MugloarRestClient::fail)
                .body(new ParameterizedTypeReference<List<MessageResponse>>() {})));
        return raw == null ? List.of() : raw.stream().map(decoder::decode).toList();
    }

    @Override
    public List<ShopItem> shop(String gameId) {
        List<ShopItemResponse> raw = backoff.call("shop", () -> guard(() -> http.get()
                .uri("/{gameId}/shop", gameId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, MugloarRestClient::fail)
                .body(new ParameterizedTypeReference<List<ShopItemResponse>>() {})));
        return raw == null
                ? List.of()
                : raw.stream().map(i -> new ShopItem(i.id(), i.name(), i.cost())).toList();
    }

    @Override
    public SolveResult solve(GameState current, String adId) {
        SolveResponse response = backoff.call("solve", () ->
                post("/{gameId}/solve/{adId}", SolveResponse.class, current.gameId(), adId));
        // The solve response has no dragon level, so it carries over from the state we came in with.
        GameState next = new GameState(
                current.gameId(), response.lives(), response.gold(), current.level(),
                response.score(), response.highScore(), response.turn());
        return new SolveResult(response.success(), response.message(), next);
    }

    @Override
    public PurchaseResult buy(GameState current, String itemId) {
        PurchaseResponse response = backoff.call("buy", () ->
                post("/{gameId}/shop/buy/{itemId}", PurchaseResponse.class, current.gameId(), itemId));
        // Purchases report no score or high score, so those carry over instead.
        GameState next = new GameState(
                current.gameId(), response.lives(), response.gold(), response.level(),
                current.score(), current.highScore(), response.turn());
        return new PurchaseResult(response.purchased(), next);
    }

    @Override
    public Reputation investigateReputation(String gameId) {
        ReputationResponse response = backoff.call("investigateReputation", () ->
                post("/{gameId}/investigate/reputation", ReputationResponse.class, gameId));
        return new Reputation(response.people(), response.state(), response.underworld());
    }

    private <T> T post(String uri, Class<T> type, Object... uriVariables) {
        return guard(() -> http.post()
                .uri(uri, uriVariables)
                .retrieve()
                .onStatus(HttpStatusCode::isError, MugloarRestClient::fail)
                .body(type));
    }

    /** Turns transport and parsing failures into the same exception type as HTTP failures. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (MugloarApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new MugloarApiException("Mugloar unreachable: " + e.getMessage(), 0, e);
        } catch (RestClientException e) {
            throw new MugloarApiException("Unreadable Mugloar response: " + e.getMessage(), 0, e);
        }
    }

    private static void fail(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        throw new MugloarApiException(
                "Mugloar returned %d for %s %s: %s".formatted(
                        status, request.getMethod(), request.getURI().getPath(), snippet(response)),
                status);
    }

    private static String snippet(ClientHttpResponse response) {
        try (var body = response.getBody()) {
            String text = new String(body.readAllBytes(), StandardCharsets.UTF_8).strip();
            return text.length() <= ERROR_SNIPPET_LIMIT
                    ? text
                    : text.substring(0, ERROR_SNIPPET_LIMIT) + "...";
        } catch (IOException e) {
            return "<unreadable body>";
        }
    }
}
