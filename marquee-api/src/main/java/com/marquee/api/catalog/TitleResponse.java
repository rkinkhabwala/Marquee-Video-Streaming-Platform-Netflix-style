package com.marquee.api.catalog;

import java.util.List;

public record TitleResponse(
        Long id,
        String type,
        String name,
        String synopsis,
        Integer releaseYear,
        String maturityRating,
        String posterKey,
        String backdropKey,
        boolean published,
        List<String> genres,
        List<SeasonResponse> seasons) {

    public static TitleResponse from(Title title, List<SeasonResponse> seasons) {
        return new TitleResponse(
                title.getId(),
                title.getType().name(),
                title.getName(),
                title.getSynopsis(),
                title.getReleaseYear(),
                title.getMaturityRating() == null ? null : title.getMaturityRating().dbValue(),
                title.getPosterKey(),
                title.getBackdropKey(),
                title.isPublished(),
                title.getGenres().stream().map(Genre::getName).sorted().toList(),
                seasons);
    }
}
