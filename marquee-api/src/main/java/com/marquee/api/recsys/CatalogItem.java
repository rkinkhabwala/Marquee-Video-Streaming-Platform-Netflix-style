package com.marquee.api.recsys;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A title in recsys' catalog contract ({@code POST /v1/catalog/items}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogItem(
        String itemId,
        String domain,
        String title,
        String artistId,
        String artistName,
        List<String> genres,
        Long durationMs,
        LocalDate releaseDate,
        Boolean explicit,
        String description) {

    private static final Set<MaturityRating> EXPLICIT = Set.of(MaturityRating.R, MaturityRating.NC_17, MaturityRating.TV_MA);

    /** @param durationSeconds a movie's playable duration; null for series or when not transcoded yet */
    public static CatalogItem from(Title title, Integer durationSeconds) {
        return new CatalogItem(
                RecsysIds.item(title.getId()),
                EngagementEventMapper.DOMAIN,
                title.getName(),
                // recsys groups items by creator; Marquee has no studio data, so all titles share one.
                "marquee",
                "Marquee",
                title.getGenres().stream().map(Genre::getName).map(name -> name.toLowerCase(Locale.ROOT)).sorted().toList(),
                durationSeconds == null ? null : durationSeconds * 1000L,
                title.getReleaseYear() == null ? null : LocalDate.of(title.getReleaseYear(), 1, 1),
                title.getMaturityRating() != null && EXPLICIT.contains(title.getMaturityRating()),
                title.getSynopsis());
    }
}
