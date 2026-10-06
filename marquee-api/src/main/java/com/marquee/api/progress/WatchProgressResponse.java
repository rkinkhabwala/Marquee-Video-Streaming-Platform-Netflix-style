package com.marquee.api.progress;

public record WatchProgressResponse(Long assetId, Integer positionSeconds, Integer durationSeconds, boolean completed) {
}
