package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row of {@code GET /:gameId/messages}, exactly as it arrives.
 *
 * <p>Two things the published docs get wrong, both found by calling the real thing:
 *
 * <ul>
 *   <li>The endpoint returns a bare JSON array, not {@code {"messages": [...]}}.
 *   <li>{@code encrypted} and {@code probability} are not documented at all.
 * </ul>
 *
 * <p>{@code reward} is typed as String because the docs say String and the live API sends a number.
 * Jackson coerces a JSON number into a String field, so binding it this way accepts both and the
 * mapper parses it once.
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
