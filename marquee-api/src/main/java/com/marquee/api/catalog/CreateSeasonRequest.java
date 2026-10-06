package com.marquee.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSeasonRequest(@NotNull Integer seasonNumber, @NotBlank String name) {
}
