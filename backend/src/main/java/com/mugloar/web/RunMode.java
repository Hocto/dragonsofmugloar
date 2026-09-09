package com.mugloar.web;

/** Who is choosing the moves. */
public enum RunMode {
    /** The strategy plays; the browser watches the SSE stream. */
    AUTO,
    /** A human picks each ad and each purchase. */
    MANUAL
}
