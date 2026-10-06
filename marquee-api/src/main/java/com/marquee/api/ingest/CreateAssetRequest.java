package com.marquee.api.ingest;

import jakarta.validation.constraints.NotNull;

public record CreateAssetRequest(@NotNull Long titleId) {
}
