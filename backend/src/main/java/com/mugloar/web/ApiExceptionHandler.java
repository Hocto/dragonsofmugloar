package com.mugloar.web;

import com.mugloar.application.port.MugloarApiException;
import com.mugloar.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every failure the frontend can see, in one shape.
 *
 * <p>The {@code retryable} flag is the point. The UI has a retry button on every async action, and
 * it should only offer it when trying again could actually work - an upstream 502 yes, a
 * nonexistent ad id no.
 *
 * <p>Upstream detail stays in the log. The browser gets told that Mugloar misbehaved and what it
 * can do about it, not the upstream URL, the status line or the game id embedded in the path -
 * none of which is the browser's business, and all of which is a small gift to anyone poking at
 * the service.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RunNotFoundException.class)
    public ResponseEntity<ApiError> runNotFound(RunNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RUN_NOT_FOUND", e.getMessage(), false));
    }

    @ExceptionHandler(MugloarApiException.class)
    public ResponseEntity<ApiError> upstream(MugloarApiException e) {
        log.warn("upstream.error status={} message=\"{}\"", e.status(), e.getMessage());
        if (e.isGameGone()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiError.of("GAME_GONE", "Mugloar no longer knows about this game.", false));
        }
        if (e.isRateLimited()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.of(
                    "UPSTREAM_RATE_LIMITED",
                    "Mugloar is rate limiting us. Give it a moment and try again.",
                    true));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of(
                "UPSTREAM_ERROR", "Mugloar did not cooperate. Worth another try.", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", e.getMessage(), false));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> badState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("BAD_STATE", e.getMessage(), false));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request body was not valid");
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", detail, false));
    }
}
