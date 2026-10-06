package com.marquee.api.catalog;

import jakarta.validation.constraints.NotBlank;

public record CreateGenreRequest(@NotBlank String name) {
}
