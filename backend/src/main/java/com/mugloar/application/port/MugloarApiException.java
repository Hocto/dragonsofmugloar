package com.mugloar.application.port;

/**
 * Anything that went wrong talking to Mugloar: a non-2xx status, a timeout, a body we could not
 * parse. {@code status} is 0 when the call never got a response, and
 * {@link #UNREADABLE_RESPONSE} when a 200 came back carrying something this client cannot use.
 */
public class MugloarApiException extends RuntimeException {

    /** A 200 whose body could not be decoded. Distinct from transport failures because a retry would decode the same bytes. */
    public static final int UNREADABLE_RESPONSE = 200;

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

    /** A 404 on a game id means the game is gone, not that the upstream is down. */
    public boolean isGameGone() {
        return status == 404;
    }

    /** The upstream rate limits with 429. */
    public boolean isRateLimited() {
        return status == 429;
    }

    /** Whether a retry could end differently: rate limits, 5xx and transport failures may; a 4xx or an unreadable body will not. */
    public boolean isWorthRetrying() {
        return isRateLimited() || status >= 500 || status == 0;
    }
}
