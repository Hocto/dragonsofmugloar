package com.mugloar.web.dto;

import java.time.Instant;

/** @param retryable whether a second attempt could succeed */
public record ApiError(String error, String message, boolean retryable, Instant at) {

    public static ApiError of(String error, String message, boolean retryable) {
        return new ApiError(error, message, retryable, Instant.now());
    }
}
