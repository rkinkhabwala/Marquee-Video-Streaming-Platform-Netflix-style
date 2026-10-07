package com.marquee.api.recsys;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Translates Marquee engagement into recsys events. recsys scores one {@code PLAY_END} per viewing
 * by completion ratio (video: ≥ 90% strong positive, ≥ 25% meaningful, < 10% abandon), so:
 * <ul>
 *   <li>25% → {@code PLAY_END} at 25%: a meaningful view, even if the viewer stops there.</li>
 *   <li>50% / 75% → {@code DWELL}: recorded, but carries no taste weight for video, so a full
 *       viewing is not counted four times.</li>
 *   <li>completed → {@code PLAY_END} at 100%: the strongest implicit signal.</li>
 * </ul>
 * Thumbs map to {@code LIKE}/{@code DISLIKE}, My List to {@code SAVE}, and search to {@code SEARCH}
 * with no query text (recsys forbids raw queries in events).
 */
public final class EngagementEventMapper {
    static final String DOMAIN = "video";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private EngagementEventMapper() {
    }

    /**
     * @param titleId null only for {@link EngagementType#SEARCH}
     * @param durationSeconds the asset duration; used for playback events
     */
    public static RecsysEvent map(EngagementType type, Long profileId, Long titleId, Integer positionSeconds,
                                  Integer durationSeconds, Instant at) {
        String eventType;
        Double value = null;
        String surface;
        Long durationMs = durationSeconds == null ? null : durationSeconds * 1000L;
        Long positionMs = positionSeconds == null ? null : positionSeconds * 1000L;

        switch (type) {
            case PLAY_START -> {
                eventType = "PLAY_START";
                surface = "player";
            }
            case PROGRESS_25 -> {
                eventType = "PLAY_END";
                value = fraction(durationSeconds, 0.25);
                positionMs = value == null ? positionMs : (long) (value * 1000);
                surface = "player";
            }
            case PROGRESS_50, PROGRESS_75 -> {
                eventType = "DWELL";
                value = fraction(durationSeconds, type == EngagementType.PROGRESS_50 ? 0.5 : 0.75);
                surface = "player";
            }
            case COMPLETED -> {
                eventType = "PLAY_END";
                value = durationSeconds == null ? null : durationSeconds.doubleValue();
                positionMs = durationMs;
                surface = "player";
            }
            case THUMBS_UP -> {
                eventType = "LIKE";
                surface = "title";
            }
            case THUMBS_DOWN -> {
                eventType = "DISLIKE";
                surface = "title";
            }
            case MY_LIST_ADD -> {
                eventType = "SAVE";
                surface = "title";
            }
            case SEARCH -> {
                eventType = "SEARCH";
                surface = "search";
            }
            default -> throw new IllegalArgumentException("Unmapped engagement type " + type);
        }

        boolean playback = surface.equals("player");
        return new RecsysEvent(
                Uuid7.generate(at.toEpochMilli()).toString(),
                RecsysIds.user(profileId),
                titleId == null ? null : RecsysIds.item(titleId),
                DOMAIN,
                eventType,
                value,
                at,
                // One session per profile per UTC day; recsys uses it for short-term context only.
                "mq-" + profileId + "-" + DAY.format(at),
                new RecsysEvent.Context("WEB", surface),
                playback ? new RecsysEvent.Media(positionMs, durationMs) : null,
                type == EngagementType.SEARCH ? Uuid7.generate(at.toEpochMilli()).toString() : null);
    }

    private static Double fraction(Integer durationSeconds, double ratio) {
        return durationSeconds == null ? null : Math.floor(durationSeconds * ratio);
    }
}
