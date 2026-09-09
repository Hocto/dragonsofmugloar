package com.mugloar.domain;

/**
 * A message from the board, already decoded and already mapped onto the risk scale.
 *
 * <p>Nothing downstream of the adapter ever sees an encoded ad, so {@code adId} here is always the
 * real id you can post to {@code /solve}.
 *
 * @param encoding kept only so the UI and the logs can show that an ad arrived obfuscated
 */
public record Ad(
        String adId,
        String message,
        int reward,
        int expiresIn,
        RiskLevel risk,
        AdEncoding encoding) {

    public Ad {
        if (adId == null || adId.isBlank()) {
            throw new IllegalArgumentException("adId is required");
        }
        if (risk == null) {
            throw new IllegalArgumentException("risk is required");
        }
        if (encoding == null) {
            throw new IllegalArgumentException("encoding is required");
        }
    }

    /** An ad with one turn left has to be attempted now or never. */
    public boolean expiresThisTurn() {
        return expiresIn <= 1;
    }
}
