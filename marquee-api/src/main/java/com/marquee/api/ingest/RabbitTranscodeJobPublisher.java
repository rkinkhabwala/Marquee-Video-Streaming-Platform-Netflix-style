package com.marquee.api.ingest;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitTranscodeJobPublisher implements TranscodeJobPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public RabbitTranscodeJobPublisher(RabbitTemplate rabbitTemplate,
                                      @Value("${app.rabbitmq.queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    @Override
    public void publish(TranscodeJobMessage job) {
        rabbitTemplate.convertAndSend(queueName, job);
    }
}
