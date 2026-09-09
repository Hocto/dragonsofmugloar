package com.mugloar.web.dto;

/**
 * An ad as the browser wants it: flat, already scored, already told whether the strategy would
 * touch it.
 *
 * <p>{@code difficultyRank} and {@code difficultyOf} exist so the UI can draw the risk scale as
 * "4 of 11" rather than as a colour. The accessibility requirement says the scale has to be
 * readable without colour, and the cleanest way to guarantee that is to send a number.
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
