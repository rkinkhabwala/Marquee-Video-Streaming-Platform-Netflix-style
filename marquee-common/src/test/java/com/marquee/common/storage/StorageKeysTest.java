package com.marquee.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StorageKeysTest {
    @Test
    void buildsKeysUsingTheSharedStorageLayout() {
        assertEquals("raw/42/source.mp4", StorageKeys.source(42L));
        assertEquals("hls/42/", StorageKeys.hlsPrefix(42L));
        assertEquals("hls/42/master.m3u8", StorageKeys.masterPlaylist(42L));
        assertEquals("thumbs/42/", StorageKeys.thumbnailsPrefix(42L));
    }
}
