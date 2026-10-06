package com.marquee.api.playback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlaybackTokenServiceTest {

    @Test
    void generatesAndValidatesToken() {
        PlaybackTokenService service = new PlaybackTokenService("stream-secret", 2L);

        String token = service.generate(42L, 9L);

        PlaybackTokenClaims claims = assertDoesNotThrow(() -> service.validate(token));
        assertEquals(42L, claims.assetId());
        assertEquals(9L, claims.profileId());
    }

    @Test
    void rejectsExpiredToken() {
        PlaybackTokenService service = new PlaybackTokenService("stream-secret", 0L);
        String token = service.generate(42L, 9L);

        assertThrows(ResponseStatusException.class, () -> service.validate(token));
    }
}
