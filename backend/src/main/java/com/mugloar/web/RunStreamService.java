package com.mugloar.web;

import com.mugloar.application.TurnEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Turns a run's event history into an SSE stream.
 *
 * <p>Subscribers get every turn that already happened before they get live ones, which is what lets
 * the UI attach late, reconnect, or open a second tab and still see a coherent game. Events are
 * numbered, so the client can tell a replay from something new.
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

        // Replay first. If the run already ended there is nothing live to wait for, so close.
        try {
            for (TurnEvent event : run.events()) {
                send(emitter, event);
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        if (!run.isRunning()) {
            emitter.complete();
            return emitter;
        }

        Consumer<TurnEvent> listener = event -> {
            try {
                send(emitter, event);
                if (event.endsRun()) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                // The browser went away mid-stream. Normal, and not worth a stack trace.
                log.debug("stream.dropped gameId={} reason=\"{}\"", run.id(), e.getMessage());
                emitter.completeWithError(e);
            }
        };

        run.onEvent(listener);
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
