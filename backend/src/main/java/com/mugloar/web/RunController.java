package com.mugloar.web;

import com.mugloar.web.dto.RunView;
import com.mugloar.web.dto.StartRunRequest;
import com.mugloar.web.dto.TurnResultView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The API the browser talks to.
 *
 * <p>This layer is the reason the browser never calls Mugloar. That is a decision, not an accident:
 * the strategy, the scoring and the ad decoding are all server side, so there is exactly one
 * implementation of each and the frontend cannot drift from it. It also means the game credentials
 * and the upstream base URL stay out of a bundle anyone can read, and CORS never enters the picture
 * because the only cross-origin call in the system is one this service makes from Java.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runs;
    private final RunStreamService streams;

    public RunController(RunService runs, RunStreamService streams) {
        this.runs = runs;
        this.streams = streams;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunView start(@Valid @RequestBody StartRunRequest request) {
        return runs.start(request.mode());
    }

    @GetMapping("/{runId}")
    public RunView get(@PathVariable String runId) {
        return runs.view(runId);
    }

    @PostMapping("/{runId}/solve")
    public TurnResultView solve(@PathVariable String runId, @Valid @RequestBody SolveRequest request) {
        return runs.solve(runId, request.adId());
    }

    @PostMapping("/{runId}/buy")
    public TurnResultView buy(@PathVariable String runId, @Valid @RequestBody BuyRequest request) {
        return runs.buy(runId, request.itemId());
    }

    /**
     * Give up the turn. No body: there is nothing to choose, which is the point of the move.
     *
     * <p>A sixth endpoint beyond the five the brief lists, added because manual mode promises the
     * same moves the bot has and the bot can do this one.
     */
    @PostMapping("/{runId}/wait")
    public TurnResultView waitOutTurn(@PathVariable String runId) {
        return runs.waitOutTurn(runId);
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        return streams.subscribe(runs.require(runId));
    }

    public record SolveRequest(@NotBlank String adId) {
    }

    public record BuyRequest(@NotBlank String itemId) {
    }
}
