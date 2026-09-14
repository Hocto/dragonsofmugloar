package com.mugloar.application;

/** What happened on a turn. One event per turn, one of these on each event. */
public enum TurnAction {
    STARTED,
    SOLVED,
    BOUGHT,
    /** Spent the turn on something harmless because attempting anything would have cost a life. */
    IDLED,
    FINISHED,
    /** The run stopped because Mugloar stopped answering, not because the dragon died. */
    FAILED
}
