package com.marquee.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTitleRequest(
        @NotNull TitleType type,
        @NotBlank String name,
        String synopsis,
        Integer releaseYear,
        String maturityRating,
        List<Long> genreIds,
        String posterKey,
        String backdropKey,
        Boolean published) {
}
