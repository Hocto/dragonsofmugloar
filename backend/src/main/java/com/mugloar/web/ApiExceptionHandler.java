package com.mugloar.web;

import com.mugloar.application.port.MugloarApiException;
import com.mugloar.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps every failure to one {@link ApiError} shape. {@code retryable} is set only when a second
 * attempt could succeed. Upstream detail (URL, status line, game id) stays in the log and is not
 * returned to the client.
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
        // Mirrors the retry rule in Backoff: 5xx and transport failures may clear, a 4xx or an
        // unreadable body will not.
        boolean retryable = e.isWorthRetrying();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of(
                "UPSTREAM_ERROR",
                retryable
                        ? "Mugloar did not cooperate. Worth another try."
                        : "Mugloar sent something this service could not use.",
                retryable));
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

    /** A body that cannot be read as the expected JSON, e.g. an unknown enum value. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_REQUEST", "Request body was not valid", false));
    }

    /**
     * Everything else. Spring's own HTTP errors (405 for a wrong method, 404 for an unknown path,
     * 415 for a wrong content type) implement {@link ErrorResponse} and keep their status; only
     * the body is reshaped. Anything unanticipated is a 500 with the detail logged, not returned.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse spring) {
            HttpStatusCode status = spring.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            String reason = known != null ? known.getReasonPhrase() : "Request could not be handled";
            return ResponseEntity.status(status)
                    .body(ApiError.of("HTTP_" + status.value(), reason, false));
        }
        log.error("unexpected.error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL", "Something went wrong on our side.", false));
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
