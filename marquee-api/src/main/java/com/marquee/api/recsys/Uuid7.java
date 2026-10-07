package com.marquee.api.recsys;

import java.security.SecureRandom;
import java.util.UUID;

/** UUIDv7 (RFC 9562): 48-bit Unix milliseconds, then random bits. recsys dedupes events on this id. */
final class Uuid7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    static UUID generate(long epochMillis) {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16
                | 0x7000L
                | ((random[0] & 0x0FL) << 8)
                | (random[1] & 0xFFL);
        long lsb = 0x8000_0000_0000_0000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            lsb |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
