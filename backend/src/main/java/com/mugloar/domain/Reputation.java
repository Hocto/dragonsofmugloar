package com.mugloar.domain;

/** Standing with the three factions, from {@code /investigate/reputation}. */
public record Reputation(double people, double state, double underworld) {

    public static final Reputation NEUTRAL = new Reputation(0, 0, 0);

    public double total() {
        return people + state + underworld;
    }
}
