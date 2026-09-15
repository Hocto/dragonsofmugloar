package com.mugloar.web;

public enum RunStatus {
    RUNNING,
    /** Lives ran out. */
    FINISHED,
    /** The upstream stopped answering. */
    FAILED
}
