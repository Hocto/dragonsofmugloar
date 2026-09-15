package com.mugloar.domain;

/**
 * How an ad's text fields arrive over the wire, signalled by the undocumented {@code encrypted}
 * field ({@code null}, {@code 1} or {@code 2}). The encoding covers {@code adId} as well, so the id
 * must be decoded before it is used in a solve call.
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
