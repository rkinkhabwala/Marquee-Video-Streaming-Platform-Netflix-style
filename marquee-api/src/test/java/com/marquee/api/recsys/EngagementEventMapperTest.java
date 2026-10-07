package com.marquee.api.recsys;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EngagementEventMapperTest {
    private static final Instant AT = Instant.parse("2026-10-07T18:30:00Z");

    @Test
    void mapsProgressToRecsysCompletionSemantics() {
        RecsysEvent quarter = EngagementEventMapper.map(EngagementType.PROGRESS_25, 7L, 42L, 160, 600, AT);
        assertThat(quarter.eventType()).isEqualTo("PLAY_END");
        assertThat(quarter.value()).isEqualTo(150.0);
        assertThat(quarter.media()).isEqualTo(new RecsysEvent.Media(150_000L, 600_000L));

        RecsysEvent half = EngagementEventMapper.map(EngagementType.PROGRESS_50, 7L, 42L, 300, 600, AT);
        assertThat(half.eventType()).isEqualTo("DWELL");
        assertThat(half.value()).isEqualTo(300.0);

        RecsysEvent done = EngagementEventMapper.map(EngagementType.COMPLETED, 7L, 42L, 590, 600, AT);
        assertThat(done.eventType()).isEqualTo("PLAY_END");
        assertThat(done.value()).isEqualTo(600.0);
        assertThat(done.media().positionMs()).isEqualTo(600_000L);
    }

    @Test
    void usesMarqueeIdsAndRecsysEnvelope() {
        RecsysEvent event = EngagementEventMapper.map(EngagementType.PLAY_START, 7L, 42L, 0, 600, AT);

        assertThat(event.userId()).isEqualTo("mq-p-7");
        assertThat(event.itemId()).isEqualTo("mq-t-42");
        assertThat(event.domain()).isEqualTo("video");
        assertThat(event.eventType()).isEqualTo("PLAY_START");
        assertThat(event.eventTs()).isEqualTo(AT);
        assertThat(event.sessionId()).isEqualTo("mq-7-20261007");
        assertThat(event.context()).isEqualTo(new RecsysEvent.Context("WEB", "player"));
        assertThat(UUID.fromString(event.eventId()).version()).isEqualTo(7);
    }

    @Test
    void mapsExplicitFeedbackAndSearch() {
        assertThat(EngagementEventMapper.map(EngagementType.THUMBS_UP, 1L, 2L, null, null, AT).eventType()).isEqualTo("LIKE");
        assertThat(EngagementEventMapper.map(EngagementType.THUMBS_DOWN, 1L, 2L, null, null, AT).eventType()).isEqualTo("DISLIKE");
        RecsysEvent save = EngagementEventMapper.map(EngagementType.MY_LIST_ADD, 1L, 2L, null, null, AT);
        assertThat(save.eventType()).isEqualTo("SAVE");
        assertThat(save.media()).isNull();

        RecsysEvent search = EngagementEventMapper.map(EngagementType.SEARCH, 1L, null, null, null, AT);
        assertThat(search.eventType()).isEqualTo("SEARCH");
        assertThat(search.itemId()).isNull();
        assertThat(search.searchQueryId()).isNotBlank();
    }

    @Test
    void uuidsAreVersion7AndTimeOrdered() {
        UUID earlier = Uuid7.generate(1_000L);
        UUID later = Uuid7.generate(2_000L);
        assertThat(earlier.version()).isEqualTo(7);
        assertThat(earlier.variant()).isEqualTo(2);
        assertThat(later.toString()).isGreaterThan(earlier.toString());
        assertThat(earlier.getMostSignificantBits() >>> 16).isEqualTo(1_000L);
    }
}
