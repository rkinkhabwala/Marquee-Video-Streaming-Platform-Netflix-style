package com.marquee.api.playback;

public record PlaybackResponse(String manifestUrl, Integer resumeAt, Integer durationSeconds) {
}
