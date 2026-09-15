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
 * The API the browser talks to. The browser never calls Mugloar directly: strategy, scoring and
 * decoding exist once, on the server, and the upstream URL stays out of the client bundle.
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

    /** Passes turns until the board changes. Takes no body; there is nothing to choose. */
    @PostMapping("/{runId}/wait")
    public TurnResultView waitForBoardToChange(@PathVariable String runId) {
        return runs.waitForBoardToChange(runId);
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
