package com.mugloar.domain;

/**
 * A message from the board, decoded and mapped onto the risk scale. {@code adId} is always the
 * decoded id accepted by {@code /solve}.
 *
 * @param encoding retained so the UI and logs can show that an ad arrived encoded
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
}
