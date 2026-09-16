package com.mugloar.web.dto;

/** Counts over the run's whole history. The event list sent to the client is capped; these are not. */
public record RunSummary(int solved, int failed, int bought, int idled) {
}
