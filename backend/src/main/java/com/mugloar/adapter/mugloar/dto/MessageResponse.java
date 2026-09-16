package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row of {@code GET /:gameId/messages} as it arrives. The endpoint returns a bare JSON array,
 * and {@code encrypted} and {@code probability} are undocumented. {@code reward} is bound as a
 * String because the docs specify String and the API sends a number; Jackson accepts both.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageResponse(
        String adId,
        String message,
        String reward,
        int expiresIn,
        Integer encrypted,
        String probability) {
}
