package com.marquee.api.playback;

import java.time.Instant;

public record PlaybackTokenClaims(Long assetId, Long profileId, Instant expiresAt) {
}
