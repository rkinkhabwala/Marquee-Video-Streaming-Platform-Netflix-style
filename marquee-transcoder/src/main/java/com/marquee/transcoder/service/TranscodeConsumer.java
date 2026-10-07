package com.marquee.transcoder.service;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.jobs.TranscodeQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs one job per message and always acks: failures are rerouted explicitly to the
 * retry queue (TTL, then back to the jobs queue) or, after the last attempt, to the DLQ.
 */
@Component
public class TranscodeConsumer {
    private static final Logger log = LoggerFactory.getLogger(TranscodeConsumer.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final TranscodeService transcodeService;
    private final TranscodeEventPublisher events;
    private final int maxAttempts;

    public TranscodeConsumer(TranscodeService transcodeService,
                             TranscodeEventPublisher events,
                             @Value("${app.transcode.max-attempts:3}") int maxAttempts) {
        this.transcodeService = transcodeService;
        this.events = events;
        this.maxAttempts = maxAttempts;
    }

    @RabbitListener(queues = TranscodeQueues.JOBS)
    public void consume(TranscodeJob job) {
        if (job == null || job.assetId() == null || job.sourceKey() == null) {
            log.error("Dropping malformed transcode job {}", job);
            return;
        }
        int attempt = Math.max(job.attempt(), 1);
        log.info("Transcoding asset {} (attempt {}/{})", job.assetId(), attempt, maxAttempts);
        events.publish(TranscodeEvent.started(job.assetId(), attempt));

        try {
            TranscodeService.Result result = transcodeService.transcode(job);
            events.publish(TranscodeEvent.succeeded(job.assetId(), attempt, result.durationSeconds(), result.masterPlaylistKey()));
        } catch (Exception e) {
            boolean willRetry = attempt < maxAttempts;
            log.warn("Transcode of asset {} failed on attempt {} (retry: {})", job.assetId(), attempt, willRetry, e);
            events.publish(TranscodeEvent.failed(job.assetId(), attempt, tail(e), willRetry));
            if (willRetry) {
                events.scheduleRetry(new TranscodeJob(job.assetId(), job.sourceKey(), attempt + 1));
            } else {
                events.deadLetter(job);
            }
        }
    }

    private static String tail(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(message.length() - MAX_ERROR_LENGTH);
    }
}
