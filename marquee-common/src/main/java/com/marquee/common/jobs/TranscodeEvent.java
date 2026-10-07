package com.marquee.common.jobs;

/** Status report published by the transcoder and consumed by the API. */
public record TranscodeEvent(Long assetId,
                             Type type,
                             int attempt,
                             Integer durationSeconds,
                             String masterPlaylistKey,
                             String error,
                             boolean willRetry) {
    public enum Type {
        STARTED,
        SUCCEEDED,
        FAILED
    }

    public static TranscodeEvent started(Long assetId, int attempt) {
        return new TranscodeEvent(assetId, Type.STARTED, attempt, null, null, null, false);
    }

    public static TranscodeEvent succeeded(Long assetId, int attempt, Integer durationSeconds, String masterPlaylistKey) {
        return new TranscodeEvent(assetId, Type.SUCCEEDED, attempt, durationSeconds, masterPlaylistKey, null, false);
    }

    public static TranscodeEvent failed(Long assetId, int attempt, String error, boolean willRetry) {
        return new TranscodeEvent(assetId, Type.FAILED, attempt, null, null, error, willRetry);
    }
}
