package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code shoppingSuccess} is documented as a String and arrives as a boolean, so it is bound as a
 * String and parsed leniently. {@code score} and {@code highScore} are absent and carry over from
 * the previous state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PurchaseResponse(
        String shoppingSuccess, int gold, int lives, int level, int turn) {

    public boolean purchased() {
        return "true".equalsIgnoreCase(String.valueOf(shoppingSuccess).strip());
    }
}
