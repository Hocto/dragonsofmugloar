package com.mugloar.web.dto;

import com.mugloar.web.RunMode;
import jakarta.validation.constraints.NotNull;

public record StartRunRequest(@NotNull RunMode mode) {
}
