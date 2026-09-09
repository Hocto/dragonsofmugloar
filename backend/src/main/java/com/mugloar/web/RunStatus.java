package com.mugloar.web;

public enum RunStatus {
    RUNNING,
    /** The dragon ran out of lives. Normal end. */
    FINISHED,
    /** Mugloar stopped answering. Not the same thing, and the UI says so. */
    FAILED
}
