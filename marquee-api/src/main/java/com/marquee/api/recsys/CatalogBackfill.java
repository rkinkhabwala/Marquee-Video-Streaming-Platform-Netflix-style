package com.marquee.api.recsys;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On start-up, queues every published title for a catalog sync, so recsys converges even if it was
 * reset or missed changes. Upserts are idempotent (recsys reports them as unchanged).
 */
@Component
public class CatalogBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CatalogBackfill.class);

    private final TitleRepository titleRepository;
    private final EngagementRecorder recorder;
    private final RecsysProperties properties;

    public CatalogBackfill(TitleRepository titleRepository, EngagementRecorder recorder, RecsysProperties properties) {
        this.titleRepository = titleRepository;
        this.recorder = recorder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        int queued = 0;
        for (Title title : titleRepository.findAll()) {
            if (title.isPublished()) {
                recorder.catalogChanged(title.getId());
                queued++;
            }
        }
        log.info("Queued {} published titles for the recsys catalog", queued);
    }
}
