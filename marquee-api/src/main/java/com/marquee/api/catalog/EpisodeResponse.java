package com.marquee.api.catalog;

public record EpisodeResponse(Long id, Integer episodeNumber, String name, String synopsis, Long videoAssetId) {
    public static EpisodeResponse from(Episode episode) {
        return new EpisodeResponse(
                episode.getId(),
                episode.getEpisodeNumber(),
                episode.getName(),
                episode.getSynopsis(),
                episode.getVideoAsset() == null ? null : episode.getVideoAsset().getId());
    }
}
