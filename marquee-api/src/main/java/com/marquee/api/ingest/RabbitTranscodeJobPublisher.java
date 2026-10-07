package com.marquee.api.ingest;

import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.jobs.TranscodeQueues;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitTranscodeJobPublisher implements TranscodeJobPublisher {
    private final RabbitTemplate rabbitTemplate;

    public RabbitTranscodeJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(TranscodeJob job) {
        rabbitTemplate.convertAndSend(TranscodeQueues.JOBS, job);
    }
}
