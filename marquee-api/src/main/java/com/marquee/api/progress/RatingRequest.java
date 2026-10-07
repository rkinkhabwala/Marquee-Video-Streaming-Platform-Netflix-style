package com.marquee.api.progress;

import jakarta.validation.constraints.NotNull;

public record RatingRequest(@NotNull Integer value) {
}
