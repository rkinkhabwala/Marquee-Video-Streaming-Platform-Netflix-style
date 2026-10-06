package com.marquee.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageKeysTest {
    @Test
    void buildsKeysUsingTheSharedStorageLayout() {
        UUID assetId = UUID.fromString("e53cb88f-c1f7-4c65-ad2b-d654f0274801");

        assertEquals("raw/e53cb88f-c1f7-4c65-ad2b-d654f0274801/source.mp4",
                StorageKeys.source(assetId));
        assertEquals("hls/e53cb88f-c1f7-4c65-ad2b-d654f0274801/master.m3u8",
                StorageKeys.masterPlaylist(assetId));
    }
}
