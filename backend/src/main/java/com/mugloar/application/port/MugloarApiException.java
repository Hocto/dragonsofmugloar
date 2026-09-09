package com.mugloar.application.port;

/**
 * Anything that went wrong talking to Mugloar: a non-2xx status, a timeout, a body we could not
 * parse. {@code status} is 0 when the call never got a response.
 */
public class MugloarApiException extends RuntimeException {

    private final int status;

    public MugloarApiException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public MugloarApiException(String message, int status) {
        this(message, status, null);
    }

    public int status() {
        return status;
    }

    /** 404 on a game id means the run is gone, not that Mugloar is broken. Worth distinguishing. */
    public boolean isGameGone() {
        return status == 404;
    }

    /** Mugloar rate limits with 429; the runner backs off rather than hammering. */
    public boolean isRateLimited() {
        return status == 429;
    }
}
