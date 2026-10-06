package com.marquee.transcoder.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TranscodeConsumer {
    private final TranscodeService transcodeService;

    public TranscodeConsumer(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void consume(TranscodeJobMessage message) {
        transcodeService.transcode(message);
    }
}
