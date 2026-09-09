package com.mugloar.adapter.mugloar;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.RiskLevel;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The client against a stubbed Mugloar. Nothing here touches the network beyond localhost, which is
 * the point: the real API rate limits, and a test suite that depends on it is a test suite that
 * fails for reasons that have nothing to do with the code.
 */
class MugloarRestClientTest {

    private WireMockServer mugloar;
    private MugloarRestClient client;

    @BeforeEach
    void startStub() {
        mugloar = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        mugloar.start();
        client = clientWith(retry(3, Duration.ofMillis(1)));
    }

    @AfterEach
    void stopStub() {
        mugloar.stop();
    }

    @Test
    void startsAGame() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/game/start")).willReturn(okJson("""
                {"gameId":"R6ymJEnj","lives":3,"gold":0,"level":0,"score":0,"highScore":12,"turn":0}
                """)));

        GameState state = client.startGame();

        assertThat(state.gameId()).isEqualTo("R6ymJEnj");
        assertThat(state.lives()).isEqualTo(3);
        assertThat(state.highScore()).isEqualTo(12);
    }

    @Test
    void readsTheBareArrayTheApiActuallyReturnsAndDecodesAsItGoes() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/g1/messages")).willReturn(okJson("""
                [
                  {"adId":"plain1","message":"Help someone","reward":82,"expiresIn":7,
                   "encrypted":null,"probability":"Risky"},
                  {"adId":"R2h0SjVLb2g=",
                   "message":"SW5maWx0cmF0ZSBUaGUgSWNlIFdvbHZlcmluZSBUcmliZSBhbmQgcmVjb3ZlciB0aGVpciBzZWNyZXRzLg==",
                   "reward":138,"expiresIn":2,"encrypted":1,"probability":"UmF0aGVyIGRldHJpbWVudGFs"},
                  {"adId":"FLw4HlD6","message":"Xvyy Pbeaé Cnvagre","reward":144,"expiresIn":2,
                   "encrypted":2,"probability":"Fhvpvqr zvffvba"}
                ]
                """)));

        List<Ad> ads = client.messages("g1");

        assertThat(ads).extracting(Ad::adId).containsExactly("plain1", "GhtJ5Koh", "SYj4UyQ6");
        assertThat(ads).extracting(Ad::encoding)
                .containsExactly(AdEncoding.NONE, AdEncoding.BASE64, AdEncoding.ROT13);
        assertThat(ads.get(2).risk()).isEqualTo(RiskLevel.SUICIDE_MISSION);
    }

    @Test
    void carriesTheDragonLevelAcrossASolveBecauseTheResponseOmitsIt() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/solve/ad1")).willReturn(okJson("""
                {"success":true,"lives":3,"gold":104,"score":104,"highScore":9,"turn":5,
                 "message":"You successfully solved the mission!"}
                """)));

        SolveResult result = client.solve(new GameState("g1", 3, 22, 4, 22, 9, 4), "ad1");

        assertThat(result.success()).isTrue();
        assertThat(result.state().level()).isEqualTo(4);
        assertThat(result.state().gold()).isEqualTo(104);
        assertThat(result.state().turn()).isEqualTo(5);
    }

    @Test
    void carriesScoreAcrossAPurchaseBecauseThatResponseOmitsItInstead() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/shop/buy/hpot")).willReturn(okJson("""
                {"shoppingSuccess":true,"gold":112,"lives":4,"level":0,"turn":10}
                """)));

        PurchaseResult result = client.buy(new GameState("g1", 3, 162, 0, 162, 9, 9), "hpot");

        assertThat(result.success()).isTrue();
        assertThat(result.state().lives()).isEqualTo(4);
        assertThat(result.state().score()).isEqualTo(162);
        assertThat(result.state().highScore()).isEqualTo(9);
    }

    @Test
    void readsAFailedPurchaseAsAFailureRatherThanAnError() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/shop/buy/hpot")).willReturn(okJson("""
                {"shoppingSuccess":false,"gold":0,"lives":3,"level":0,"turn":1}
                """)));

        assertThat(client.buy(new GameState("g1", 3, 0, 0, 0, 0, 0), "hpot").success()).isFalse();
    }

    @Test
    void readsShoppingSuccessWhenItArrivesAsAStringInsteadOfABoolean() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/shop/buy/hpot")).willReturn(okJson("""
                {"shoppingSuccess":"True","gold":112,"lives":4,"level":0,"turn":10}
                """)));

        assertThat(client.buy(new GameState("g1", 3, 162, 0, 162, 0, 9), "hpot").success()).isTrue();
    }

    @Test
    void readsTheShopListing() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/g1/shop")).willReturn(okJson("""
                [{"id":"hpot","name":"Healing potion","cost":50},
                 {"id":"cs","name":"Claw Sharpening","cost":100}]
                """)));

        List<ShopItem> shop = client.shop("g1");

        assertThat(shop).containsExactly(
                new ShopItem("hpot", "Healing potion", 50),
                new ShopItem("cs", "Claw Sharpening", 100));
    }

    @Test
    void readsReputation() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/investigate/reputation"))
                .willReturn(okJson("""
                        {"people":0.3,"state":-1,"underworld":2}
                        """)));

        assertThat(client.investigateReputation("g1")).isEqualTo(new Reputation(0.3, -1, 2));
    }

    @Test
    void retriesRateLimitingAndThenSucceeds() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/game/start"))
                .inScenario("rate limit").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withBody("error code: 1015"))
                .willSetStateTo("second try"));
        mugloar.stubFor(post(urlEqualTo("/api/v2/game/start"))
                .inScenario("rate limit").whenScenarioStateIs("second try")
                .willReturn(okJson("""
                        {"gameId":"g9","lives":3,"gold":0,"level":0,"score":0,"highScore":0,"turn":0}
                        """)));

        assertThat(client.startGame().gameId()).isEqualTo("g9");
        mugloar.verify(2, postRequestedFor(urlEqualTo("/api/v2/game/start")));
    }

    @Test
    void givesUpOnceTheRetryBudgetIsSpent() {
        mugloar.stubFor(post(urlEqualTo("/api/v2/game/start"))
                .willReturn(aResponse().withStatus(503).withBody("nope")));

        assertThatThrownBy(() -> client.startGame())
                .isInstanceOfSatisfying(MugloarApiException.class,
                        e -> assertThat(e.status()).isEqualTo(503));
        mugloar.verify(3, postRequestedFor(urlEqualTo("/api/v2/game/start")));
    }

    @Test
    void doesNotRetryARequestThatWasWrongTheFirstTime() {
        // The HTML body is what Mugloar really sends on a 400, so this also checks we do not try
        // to parse an error as JSON.
        mugloar.stubFor(post(urlEqualTo("/api/v2/g1/solve/bogus")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody("<!DOCTYPE html><html><body><pre>Bad Request</pre></body></html>")));

        assertThatThrownBy(() -> client.solve(new GameState("g1", 3, 0, 0, 0, 0, 0), "bogus"))
                .isInstanceOfSatisfying(MugloarApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.getMessage()).contains("Bad Request");
                });
        mugloar.verify(1, postRequestedFor(urlEqualTo("/api/v2/g1/solve/bogus")));
    }

    @Test
    void recognisesAGameThatNoLongerExists() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/gone/messages"))
                .willReturn(aResponse().withStatus(404).withBody("Not Found")));

        assertThatThrownBy(() -> client.messages("gone"))
                .isInstanceOfSatisfying(MugloarApiException.class,
                        e -> assertThat(e.isGameGone()).isTrue());
        mugloar.verify(1, getRequestedFor(urlEqualTo("/api/v2/gone/messages")));
    }

    @Test
    void surfacesATimeoutAsAnUpstreamFailureRatherThanHanging() {
        MugloarRestClient impatient = clientWith(retry(1, Duration.ofMillis(1)), Duration.ofMillis(150));
        mugloar.stubFor(get(urlEqualTo("/api/v2/g1/shop"))
                .willReturn(okJson("[]").withFixedDelay(1_500)));

        assertThatThrownBy(() -> impatient.shop("g1"))
                .isInstanceOfSatisfying(MugloarApiException.class,
                        e -> assertThat(e.status()).isZero());
    }

    @Test
    void sendsNoAuthenticationOrCookiesBecauseTheApiWantsNone() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/g1/shop")).willReturn(okJson("[]")));

        client.shop("g1");

        mugloar.verify(getRequestedFor(urlEqualTo("/api/v2/g1/shop"))
                .withoutHeader("Authorization")
                .withoutHeader("Cookie"));
    }

    @Test
    void urlEncodesIdsInsteadOfPastingThemIntoThePath() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/a%20b/shop")).willReturn(okJson("[]")));

        client.shop("a b");

        mugloar.verify(getRequestedFor(urlEqualTo("/api/v2/a%20b/shop")));
    }

    @Test
    void treatsAnEmptyBoardAsEmptyRatherThanNull() {
        mugloar.stubFor(get(urlEqualTo("/api/v2/g1/messages")).willReturn(okJson("[]")));

        assertThat(client.messages("g1")).isEmpty();
    }

    private MugloarProperties.Retry retry(int attempts, Duration backoff) {
        return new MugloarProperties.Retry(attempts, backoff, 1.0, backoff);
    }

    private MugloarRestClient clientWith(MugloarProperties.Retry retry) {
        return clientWith(retry, Duration.ofSeconds(5));
    }

    private MugloarRestClient clientWith(MugloarProperties.Retry retry, Duration readTimeout) {
        MugloarProperties properties = new MugloarProperties(
                mugloar.baseUrl() + "/api/v2", Duration.ofSeconds(2), readTimeout, retry);
        var httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
        return new MugloarRestClient(restClient, new AdDecoder(), properties);
    }

    @Test
    void doesNotCallTheRealApi() {
        assertThat(mugloar.baseUrl()).startsWith("http://localhost:");
    }
}
