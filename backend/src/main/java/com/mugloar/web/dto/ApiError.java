package com.mugloar.web.dto;

import java.time.Instant;

/**
 * @param retryable whether the frontend's retry button is worth showing; upstream hiccups are,
 *                  a bad ad id is not
 */
public record ApiError(String error, String message, boolean retryable, Instant at) {

    public static ApiError of(String error, String message, boolean retryable) {
        return new ApiError(error, message, retryable, Instant.now());
    }
}
