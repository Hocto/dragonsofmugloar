package com.mugloar.application.port;

/**
 * Anything that went wrong talking to Mugloar: a non-2xx status, a timeout, a body we could not
 * parse. {@code status} is 0 when the call never got a response, and
 * {@link #UNREADABLE_RESPONSE} when a 200 came back carrying something this client cannot use.
 */
public class MugloarApiException extends RuntimeException {

    /**
     * A 200 whose body could not be turned into domain objects: an unknown encoding, malformed
     * Base64, a non-numeric reward. Kept distinct from transport failures because retrying it
     * would just decode the same bytes again.
     */
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

    /** 404 on a game id means the run is gone, not that Mugloar is broken. Worth distinguishing. */
    public boolean isGameGone() {
        return status == 404;
    }

    /** Mugloar rate limits with 429; the runner backs off rather than hammering. */
    public boolean isRateLimited() {
        return status == 429;
    }

    /**
     * Whether a second attempt could plausibly end differently. Rate limits clear, servers
     * recover, connections come back; a 4xx or an unreadable body will do the same thing twice.
     */
    public boolean isWorthRetrying() {
        return isRateLimited() || status >= 500 || status == 0;
    }
}
