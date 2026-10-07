package com.marquee.transcoder.service;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import com.marquee.common.jobs.TranscodeQueues;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitTranscodeEventPublisher implements TranscodeEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public RabbitTranscodeEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(TranscodeEvent event) {
        rabbitTemplate.convertAndSend(TranscodeQueues.EVENTS, event);
    }

    @Override
    public void scheduleRetry(TranscodeJob job) {
        rabbitTemplate.convertAndSend(TranscodeQueues.RETRY, job);
    }

    @Override
    public void deadLetter(TranscodeJob job) {
        rabbitTemplate.convertAndSend(TranscodeQueues.DEAD_LETTER, job);
    }
}
