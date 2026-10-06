package com.marquee.api.progress;

public record NextEpisodeResponse(Long episodeId,
                                 Long seasonId,
                                 Integer seasonNumber,
                                 Integer episodeNumber,
                                 String name,
                                 Long videoAssetId,
                                 Long titleId) {
}
