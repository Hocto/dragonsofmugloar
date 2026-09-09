package com.mugloar.domain;

/**
 * How an ad's text fields arrive over the wire.
 *
 * <p>The API signals this with an {@code encrypted} field that is {@code null}, {@code 1} or
 * {@code 2}. None of it is in the published docs; the values came from watching real responses.
 * Note that the encoding covers {@code adId} too, so the id has to be decoded before it can be
 * used in a solve call.
 */
public enum AdEncoding {
    /** {@code encrypted: null} - plain text. */
    NONE,
    /** {@code encrypted: 1} - Base64. */
    BASE64,
    /** {@code encrypted: 2} - ROT13, leaving non-ASCII letters alone. */
    ROT13;

    public static AdEncoding fromWire(Integer encrypted) {
        if (encrypted == null) {
            return NONE;
        }
        return switch (encrypted) {
            case 1 -> BASE64;
            case 2 -> ROT13;
            default -> throw new IllegalArgumentException("Unknown ad encoding: " + encrypted);
        };
    }
}
