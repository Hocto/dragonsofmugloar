package com.mugloar.web;

import com.mugloar.application.TurnEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes a run's events as an SSE stream. Subscribers receive a replay of the history before live
 * events, and events are numbered so the client can discard duplicates.
 */
@Service
public class RunStreamService {

    private static final Logger log = LoggerFactory.getLogger(RunStreamService.class);

    private final Duration timeout;

    public RunStreamService(WebProperties properties) {
        this.timeout = properties.streamTimeout();
    }

    public SseEmitter subscribe(Run run) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());

        Consumer<TurnEvent> listener = event -> {
            try {
                send(emitter, event);
                if (event.endsRun()) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                // The client disconnected mid-stream.
                log.debug("stream.dropped gameId={} reason=\"{}\"", run.id(), e.getMessage());
                emitter.completeWithError(e);
            }
        };

        // Snapshot and subscribe atomically; anything that lands in both is discarded by sequence
        // number on the client.
        List<TurnEvent> history = run.replayAndSubscribe(listener);
        try {
            for (TurnEvent event : history) {
                send(emitter, event);
            }
        } catch (IOException e) {
            run.removeListener(listener);
            emitter.completeWithError(e);
            return emitter;
        }

        if (!run.isRunning()) {
            run.removeListener(listener);
            emitter.complete();
            return emitter;
        }

        emitter.onCompletion(() -> run.removeListener(listener));
        emitter.onTimeout(() -> {
            run.removeListener(listener);
            emitter.complete();
        });
        emitter.onError(error -> run.removeListener(listener));
        return emitter;
    }

    private void send(SseEmitter emitter, TurnEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(event.sequence()))
                .name("turn")
                .data(event));
    }
}
