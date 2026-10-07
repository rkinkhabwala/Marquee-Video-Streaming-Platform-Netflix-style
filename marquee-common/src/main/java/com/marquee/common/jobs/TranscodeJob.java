package com.marquee.common.jobs;

/** Work item published by the API and consumed by the transcoder. {@code attempt} starts at 1. */
public record TranscodeJob(Long assetId, String sourceKey, int attempt) {
    public TranscodeJob nextAttempt() {
        return new TranscodeJob(assetId, sourceKey, attempt + 1);
    }
}
