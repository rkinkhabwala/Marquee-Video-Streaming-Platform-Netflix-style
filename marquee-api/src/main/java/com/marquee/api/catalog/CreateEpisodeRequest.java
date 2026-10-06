package com.marquee.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEpisodeRequest(
        @NotNull Integer episodeNumber,
        @NotBlank String name,
        String synopsis,
        Long videoAssetId) {
}
