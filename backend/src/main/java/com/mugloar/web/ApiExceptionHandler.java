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
        HttpStatus status = e.isRateLimited() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(ApiError.of("UPSTREAM_ERROR", "Mugloar did not cooperate: " + e.getMessage(), true));
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
