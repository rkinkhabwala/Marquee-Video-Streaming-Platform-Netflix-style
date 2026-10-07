package com.marquee.api.recsys;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records engagement and catalog changes for recsys in the outbox, inside the caller's transaction
 * (so an event exists exactly when the change it describes was committed). No-op when recsys is
 * disabled.
 */
@Component
public class EngagementRecorder {
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final RecsysProperties properties;
    private final Clock clock;

    public EngagementRecorder(OutboxRepository outbox, ObjectMapper objectMapper, RecsysProperties properties, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(EngagementType type, Long profileId, Long titleId, Integer positionSeconds, Integer durationSeconds) {
        if (!properties.enabled()) {
            return;
        }
        RecsysEvent event = EngagementEventMapper.map(type, profileId, titleId, positionSeconds, durationSeconds, clock.instant());
        try {
            outbox.save(new OutboxEntry(OutboxEntry.Kind.EVENT, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize recsys event", e);
        }
    }

    /** The title was created, changed, published or deleted; recsys gets its state at delivery time. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void catalogChanged(Long titleId) {
        if (properties.enabled()) {
            outbox.save(new OutboxEntry(OutboxEntry.Kind.CATALOG, String.valueOf(titleId)));
        }
    }

    /** Right to erasure: the profile is gone, so its recsys history must go too. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void profileDeleted(Long profileId) {
        if (properties.enabled()) {
            outbox.save(new OutboxEntry(OutboxEntry.Kind.USER_DELETE, String.valueOf(profileId)));
        }
    }
}
