package com.marquee.transcoder.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public Queue transcodeQueue(@Value("${app.rabbitmq.queue}") String queueName,
                                @Value("${app.rabbitmq.dlq}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean
    public Queue transcodeDlq(@Value("${app.rabbitmq.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public TopicExchange transcodeExchange() {
        return new TopicExchange("marquee.transcode.exchange");
    }

    @Bean
    public Binding transcodeBinding(Queue transcodeQueue, TopicExchange transcodeExchange) {
        return BindingBuilder.bind(transcodeQueue)
                .to(transcodeExchange)
                .with("marquee.transcode");
    }
}
