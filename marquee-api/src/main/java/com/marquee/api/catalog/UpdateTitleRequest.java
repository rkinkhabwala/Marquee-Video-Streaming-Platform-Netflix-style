package com.marquee.api.catalog;

import java.util.List;

public record UpdateTitleRequest(
        TitleType type,
        String name,
        String synopsis,
        Integer releaseYear,
        String maturityRating,
        List<Long> genreIds,
        String posterKey,
        String backdropKey,
        Boolean published) {
}
