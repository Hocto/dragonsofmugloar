package com.mugloar.domain;

/** What came back from a shop purchase, plus the resulting state. */
public record PurchaseResult(boolean success, GameState state) {
}
