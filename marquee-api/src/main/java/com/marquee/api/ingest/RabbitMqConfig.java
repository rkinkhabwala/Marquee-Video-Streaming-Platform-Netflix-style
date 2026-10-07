package com.marquee.api.ingest;

import com.marquee.common.jobs.TranscodeQueues;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the queues the API touches; arguments must match the transcoder's declarations. */
@Configuration
public class RabbitMqConfig {
    @Bean
    public Queue transcodeJobsQueue() {
        return QueueBuilder.durable(TranscodeQueues.JOBS).build();
    }

    @Bean
    public Queue transcodeEventsQueue() {
        return QueueBuilder.durable(TranscodeQueues.EVENTS).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter("com.marquee.common.jobs");
    }
}
