package com.marquee.api.catalog;

import java.util.List;

public record SeasonResponse(Long id, Integer seasonNumber, String name, List<EpisodeResponse> episodes) {
    public static SeasonResponse from(Season season, List<Episode> episodes) {
        return new SeasonResponse(
                season.getId(),
                season.getSeasonNumber(),
                season.getName(),
                episodes.stream().map(EpisodeResponse::from).toList());
    }
}
