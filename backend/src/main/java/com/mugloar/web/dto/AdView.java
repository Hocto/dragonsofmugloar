package com.mugloar.web.dto;

/**
 * An ad as the browser renders it: flat, scored, and flagged with the strategy's verdict.
 * {@code difficultyRank} and {@code difficultyOf} let the UI express difficulty as "4 of 11" so it
 * is readable without colour.
 */
public record AdView(
        String adId,
        String message,
        int reward,
        int expiresIn,
        String risk,
        int difficultyRank,
        int difficultyOf,
        boolean wasEncoded,
        String encoding,
        Double successChance,
        Double score,
        boolean recommended,
        boolean skippedByStrategy) {
}
