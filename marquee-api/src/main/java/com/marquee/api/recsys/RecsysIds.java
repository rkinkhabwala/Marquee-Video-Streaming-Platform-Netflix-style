package com.marquee.api.recsys;

import java.util.Optional;

/**
 * Marquee identifiers as seen by recsys. Recommendations are per profile (each profile has its own
 * taste), and items are titles: a series is one item, whichever episode was watched.
 */
public final class RecsysIds {
    static final String USER_PREFIX = "mq-p-";
    static final String ITEM_PREFIX = "mq-t-";

    private RecsysIds() {
    }

    public static String user(Long profileId) {
        return USER_PREFIX + profileId;
    }

    public static String item(Long titleId) {
        return ITEM_PREFIX + titleId;
    }

    /** The title id for a Marquee item id; empty for anything else (recsys may return foreign ids). */
    public static Optional<Long> titleId(String itemId) {
        if (itemId == null || !itemId.startsWith(ITEM_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(itemId.substring(ITEM_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
