package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code shoppingSuccess} is documented as a String and has been seen as both a JSON boolean and
 * the strings "True"/"False", so it is bound as a String and parsed leniently.
 *
 * <p>No {@code score} or {@code highScore} here; those carry over from the previous state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PurchaseResponse(
        String shoppingSuccess, int gold, int lives, int level, int turn) {

    public boolean purchased() {
        return "true".equalsIgnoreCase(String.valueOf(shoppingSuccess).strip());
    }
}
