package com.marquee.api.recsys;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers the outbox to recsys: catalog changes first (so items exist before events about them),
 * then events in batches, then user deletions. Delivered entries are deleted; failed ones stay and
 * are retried on the next run. Events older than 24 h are dropped, because recsys treats them as
 * late and uses them offline only.
 */
@Component
public class RecsysOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(RecsysOutboxRelay.class);
    static final Duration EVENT_RETENTION = Duration.ofHours(24);

    private final OutboxRepository outbox;
    private final RecsysClient client;
    private final TitleRepository titleRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final ObjectMapper objectMapper;
    private final RecsysProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public RecsysOutboxRelay(OutboxRepository outbox, RecsysClient client, TitleRepository titleRepository,
                             VideoAssetRepository videoAssetRepository, ObjectMapper objectMapper,
                             RecsysProperties properties, TransactionTemplate transactions, Clock clock) {
        this.outbox = outbox;
        this.client = client;
        this.titleRepository = titleRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.recsys.relay-interval-ms:2000}", initialDelayString = "${app.recsys.relay-interval-ms:2000}")
    public void scheduledRun() {
        if (properties.enabled() && properties.relayEnabled()) {
            try {
                relayOnce();
            } catch (RuntimeException e) {
                log.warn("recsys outbox relay run failed: {}", e.getMessage());
            }
        }
    }

    /** Delivers one batch; returns how many entries were delivered (or dropped as permanently invalid). */
    public int relayOnce() {
        Integer done = transactions.execute(status -> {
            outbox.deleteOlderThan(OutboxEntry.Kind.EVENT, clock.instant().minus(EVENT_RETENTION));
            List<OutboxEntry> batch = outbox.lockBatch(properties.relayBatchSize());
            Map<OutboxEntry.Kind, List<OutboxEntry>> byKind = batch.stream()
                    .collect(Collectors.groupingBy(OutboxEntry::getKind));
            int delivered = 0;
            delivered += deliver(byKind.getOrDefault(OutboxEntry.Kind.CATALOG, List.of()), this::sendCatalog);
            delivered += deliver(byKind.getOrDefault(OutboxEntry.Kind.EVENT, List.of()), this::sendEvents);
            delivered += deliver(byKind.getOrDefault(OutboxEntry.Kind.USER_DELETE, List.of()), this::sendUserDeletes);
            return delivered;
        });
        return done == null ? 0 : done;
    }

    private int deliver(List<OutboxEntry> entries, Function<List<OutboxEntry>, Boolean> sender) {
        if (entries.isEmpty()) {
            return 0;
        }
        boolean drop;
        try {
            drop = sender.apply(entries);
        } catch (RecsysClient.RecsysUnavailableException e) {
            if (e.getCause() != null && HttpRecsysClient.isClientError(e.getCause())) {
                // A 4xx means the payload itself is wrong; retrying would fail forever.
                log.error("recsys rejected {} {} entries permanently: {}", entries.size(), entries.get(0).getKind(), e.getMessage());
                drop = true;
            } else {
                entries.forEach(OutboxEntry::recordFailedAttempt);
                log.debug("recsys unavailable, keeping {} {} entries: {}", entries.size(), entries.get(0).getKind(), e.getMessage());
                return 0;
            }
        }
        if (drop) {
            outbox.deleteAll(entries);
            return entries.size();
        }
        return 0;
    }

    private boolean sendEvents(List<OutboxEntry> entries) {
        List<RecsysEvent> events = entries.stream().map(this::readEvent).toList();
        int accepted = client.sendEvents(events);
        if (accepted < events.size()) {
            log.warn("recsys rejected {} of {} events as invalid", events.size() - accepted, events.size());
        }
        return true;
    }

    private boolean sendCatalog(List<OutboxEntry> entries) {
        Set<Long> titleIds = entries.stream().map(e -> Long.valueOf(e.getPayload())).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Title> titles = titleRepository.findAllById(titleIds).stream().collect(Collectors.toMap(Title::getId, t -> t));
        List<CatalogItem> upserts = new ArrayList<>();
        for (Long titleId : titleIds) {
            Title title = titles.get(titleId);
            if (title == null || !title.isPublished()) {
                client.deleteCatalogItem(RecsysIds.item(titleId));
            } else {
                upserts.add(CatalogItem.from(title, playableDuration(title)));
            }
        }
        if (!upserts.isEmpty()) {
            client.upsertCatalog(upserts);
        }
        return true;
    }

    private boolean sendUserDeletes(List<OutboxEntry> entries) {
        for (OutboxEntry entry : entries) {
            client.deleteUserData(RecsysIds.user(Long.valueOf(entry.getPayload())));
        }
        return true;
    }

    private Integer playableDuration(Title title) {
        if (title.getType() != TitleType.MOVIE) {
            return null;
        }
        return videoAssetRepository.findFirstByTitle_IdAndStatusOrderByCreatedAtDesc(title.getId(), VideoAssetStatus.READY)
                .map(VideoAsset::getDurationSeconds)
                .orElse(null);
    }

    private RecsysEvent readEvent(OutboxEntry entry) {
        try {
            return objectMapper.readValue(entry.getPayload(), RecsysEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt recsys outbox entry " + entry.getId(), e);
        }
    }
}
