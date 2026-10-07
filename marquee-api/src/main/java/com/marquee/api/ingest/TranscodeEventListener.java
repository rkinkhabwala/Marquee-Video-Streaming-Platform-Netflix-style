package com.marquee.api.ingest;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeQueues;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TranscodeEventListener {
    private final IngestService ingestService;

    public TranscodeEventListener(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @RabbitListener(queues = TranscodeQueues.EVENTS)
    public void onEvent(TranscodeEvent event) {
        ingestService.applyTranscodeEvent(event);
    }
}
