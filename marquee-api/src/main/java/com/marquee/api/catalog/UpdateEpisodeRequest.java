package com.marquee.api.catalog;

public record UpdateEpisodeRequest(Integer episodeNumber, String name, String synopsis, Long videoAssetId) {
}
