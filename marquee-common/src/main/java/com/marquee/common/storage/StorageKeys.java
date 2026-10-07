package com.marquee.common.storage;

/** Object keys in the {@code marquee} bucket; see the storage layout in spec.md. */
public final class StorageKeys {
    private StorageKeys() {
    }

    public static String source(Long assetId) {
        return "raw/" + assetId + "/source.mp4";
    }

    public static String hlsPrefix(Long assetId) {
        return "hls/" + assetId + "/";
    }

    public static String masterPlaylist(Long assetId) {
        return hlsPrefix(assetId) + "master.m3u8";
    }

    public static String thumbnailsPrefix(Long assetId) {
        return "thumbs/" + assetId + "/";
    }
}
