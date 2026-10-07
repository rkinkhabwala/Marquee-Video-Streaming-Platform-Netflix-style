package com.marquee.transcoder.config;

import com.marquee.common.jobs.TranscodeQueues;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public Queue transcodeJobsQueue() {
        return QueueBuilder.durable(TranscodeQueues.JOBS).build();
    }

    @Bean
    public Queue transcodeRetryQueue(@Value("${app.transcode.retry-delay-ms:10000}") int retryDelayMs) {
        return QueueBuilder.durable(TranscodeQueues.RETRY)
                .ttl(retryDelayMs)
                .deadLetterExchange("")
                .deadLetterRoutingKey(TranscodeQueues.JOBS)
                .build();
    }

    @Bean
    public Queue transcodeDeadLetterQueue() {
        return QueueBuilder.durable(TranscodeQueues.DEAD_LETTER).build();
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
