package com.marquee.api.catalog;

import java.util.List;

public record TitleDetailResponse(Long id,
                                  String type,
                                  String name,
                                  String synopsis,
                                  Integer releaseYear,
                                  String maturityRating,
                                  String posterKey,
                                  String backdropKey,
                                  List<String> genres,
                                  boolean inMyList,
                                  Integer rating,
                                  /* Movies only: the playable asset, if one is READY. */
                                  Long videoAssetId,
                                  Integer durationSeconds,
                                  Integer resumeAt,
                                  List<SeasonDetail> seasons) {
    public record SeasonDetail(Long id, Integer seasonNumber, String name, List<EpisodeDetail> episodes) {
    }

    public record EpisodeDetail(Long id,
                                Integer episodeNumber,
                                String name,
                                String synopsis,
                                Long videoAssetId,
                                Integer durationSeconds,
                                boolean playable,
                                Integer resumeAt) {
    }
}
