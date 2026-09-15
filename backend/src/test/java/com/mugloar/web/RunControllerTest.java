package com.mugloar.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mugloar.application.TurnEvent;
import com.mugloar.application.port.MugloarApiException;
import com.mugloar.domain.GameState;
import com.mugloar.web.dto.AdView;
import com.mugloar.web.dto.RunSummary;
import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.ShopAdviceView;
import com.mugloar.web.dto.ShopItemView;
import com.mugloar.web.dto.TurnResultView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * A slice over the controller only. The point is the HTTP contract the Vue client codes against:
 * status codes, JSON shape, and what a failure looks like.
 */
@WebMvcTest(controllers = RunController.class)
@TestPropertySource(properties = "mugloar.web.allowed-origins=")
class RunControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RunService runs;

    @MockBean
    private RunStreamService streams;

    @TestConfiguration
    static class Properties {
        @Bean
        WebProperties webProperties() {
            return new WebProperties(
                    List.of(), java.time.Duration.ZERO, 10, java.time.Duration.ofMinutes(1));
        }
    }

    @Test
    void startingARunReturns201AndTheBoard() throws Exception {
        given(runs.start(RunMode.AUTO)).willReturn(sampleView());

        mvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"AUTO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value("g1"))
                .andExpect(jsonPath("$.mode").value("AUTO"))
                .andExpect(jsonPath("$.state.lives").value(3))
                .andExpect(jsonPath("$.ads[0].adId").value("ad1"))
                .andExpect(jsonPath("$.ads[0].difficultyRank").value(1))
                .andExpect(jsonPath("$.ads[0].difficultyOf").value(11))
                .andExpect(jsonPath("$.shop[0].affordable").value(false))
                .andExpect(jsonPath("$.shopAdvice.action").value("SKIP"));
    }

    @Test
    void rejectsARunWithNoMode() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void rejectsAModeThatIsNotAModeInTheSameErrorShape() throws Exception {
        // An enum value that does not exist fails before validation runs. The client should still
        // get an ApiError with a message, not Spring's default body.
        mvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CHEAT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void returnsTheCurrentRun() throws Exception {
        given(runs.view("g1")).willReturn(sampleView());

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.strategy").value("expected-value"));
    }

    @Test
    void unknownRunIs404WithAnErrorTheUiCanRender() throws Exception {
        given(runs.view("nope")).willThrow(new RunNotFoundException("nope"));

        mvc.perform(get("/api/runs/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void solvingAnAdPassesTheIdThroughAndReturnsTheDelta() throws Exception {
        given(runs.solve("g1", "ad1")).willReturn(new TurnResultView(
                TurnEvent.finished(3, state(), "Out of lives"), sampleView()));

        mvc.perform(post("/api/runs/g1/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adId":"ad1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.action").value("FINISHED"))
                .andExpect(jsonPath("$.run.runId").value("g1"));

        verify(runs).solve("g1", "ad1");
    }

    @Test
    void rejectsASolveWithNoAdId() throws Exception {
        mvc.perform(post("/api/runs/g1/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adId":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buyingAnItemPassesTheIdThrough() throws Exception {
        given(runs.buy("g1", "hpot")).willReturn(new TurnResultView(
                TurnEvent.started(state()), sampleView()));

        mvc.perform(post("/api/runs/g1/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"hpot"}
                                """))
                .andExpect(status().isOk());

        verify(runs).buy("g1", "hpot");
    }

    @Test
    void waitingOutATurnNeedsNoBodyAndReturnsTheDelta() throws Exception {
        given(runs.waitForBoardToChange("g1")).willReturn(new TurnResultView(
                TurnEvent.idled(4, "Nothing worth attempting", state(), state()), sampleView()));

        mvc.perform(post("/api/runs/g1/wait"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.action").value("IDLED"))
                .andExpect(jsonPath("$.run.runId").value("g1"));

        verify(runs).waitForBoardToChange("g1");
    }

    @Test
    void waitingOutATurnOnAnAutoRunIsAConflict() throws Exception {
        willThrow(new IllegalStateException("Run g1 is playing itself; watch the stream instead"))
                .given(runs).waitForBoardToChange(any());

        mvc.perform(post("/api/runs/g1/wait"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BAD_STATE"));
    }

    @Test
    void manualActionsOnAnAutoRunAreAConflictNotACrash() throws Exception {
        willThrow(new IllegalStateException("Run g1 is playing itself; watch the stream instead"))
                .given(runs).solve(any(), any());

        mvc.perform(post("/api/runs/g1/solve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adId":"ad1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BAD_STATE"));
    }

    @Test
    void anUpstreamFailureIsA502TheUiIsAllowedToRetry() throws Exception {
        given(runs.view("g1")).willThrow(new MugloarApiException(
                "Mugloar returned 503 for GET /api/v2/secret-game-id/messages", 503));

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                // The upstream URL and the game id stay in the log, not in the browser.
                .andExpect(jsonPath("$.message").value(not(containsString("secret-game-id"))))
                .andExpect(jsonPath("$.message").value(not(containsString("/api/v2"))));
    }

    @Test
    void anUnreadableUpstreamPayloadIsUpstreamsFaultNotTheClients() throws Exception {
        // Mugloar sent a 200 the adapter could not decode. That is not "your request was bad", and
        // asking again would decode the same bytes again, so it is 502 and not retryable.
        given(runs.view("g1")).willThrow(new MugloarApiException(
                "Mugloar sent an ad this client cannot read: Unknown ad encoding: 7",
                MugloarApiException.UNREADABLE_RESPONSE));

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void anUpstream4xxIsNotOfferedAsRetryable() throws Exception {
        given(runs.view("g1")).willThrow(new MugloarApiException("Mugloar returned 400", 400));

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void anUnexpectedExceptionStillReturnsTheErrorShapeWithoutTheDetail() throws Exception {
        given(runs.view("g1")).willThrow(new NullPointerException("secret internal detail"));

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))));
    }

    @Test
    void rateLimitingIsPassedThroughAs429() throws Exception {
        given(runs.view("g1")).willThrow(new MugloarApiException("error code: 1015", 429));

        mvc.perform(get("/api/runs/g1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("UPSTREAM_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void theStreamHandsTheRunToTheStreamServiceAndGoesAsync() throws Exception {
        Run run = new Run(state(), RunMode.AUTO, "expected-value");
        given(runs.require("g1")).willReturn(run);
        given(streams.subscribe(run)).willReturn(new SseEmitter(1000L));

        mvc.perform(get("/api/runs/g1/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(streams).subscribe(run);
    }

    private static GameState state() {
        return new GameState("g1", 3, 20, 0, 0, 0, 1);
    }

    private static RunView sampleView() {
        return new RunView(
                "g1", "AUTO", "RUNNING", "expected-value",
                state(), null, null,
                List.of(new AdView("ad1", "Help someone", 82, 7, "Piece of cake", 1, 11,
                        false, "NONE", 0.86, 91.0, true, false)),
                List.of(new ShopItemView("hpot", "Healing potion", 50, false, true, false)),
                new ShopAdviceView("SKIP", null, "nothing worth buying at 20 gold"),
                List.of(),
                new RunSummary(0, 0, 0, 0));
    }
}
