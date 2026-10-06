package com.marquee.common.storage;

import java.util.UUID;

public final class StorageKeys {
    private StorageKeys() {
    }

    public static String source(UUID assetId) {
        return "raw/" + assetId + "/source.mp4";
    }

    public static String masterPlaylist(UUID assetId) {
        return "hls/" + assetId + "/master.m3u8";
    }
}
