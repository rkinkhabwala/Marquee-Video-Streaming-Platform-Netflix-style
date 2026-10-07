package com.marquee.api.recsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RecsysProperties.class)
public class RecsysConfig {
    private static final Logger log = LoggerFactory.getLogger(RecsysConfig.class);

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(RecsysClient.class)
    RecsysClient recsysClient(RecsysProperties properties, ObjectMapper objectMapper) {
        return new HttpRecsysClient(properties, objectMapper, breaker("recsys-serving"), breaker("recsys-delivery"));
    }

    /**
     * Opens after half of the last 10 calls failed (once at least 5 were made), then lets 2 trial
     * calls through after 30 s. 4xx responses are our bugs, not an outage, so they do not count.
     */
    static CircuitBreaker breaker(String name) {
        CircuitBreaker breaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreException(HttpRecsysClient::isClientError)
                .build());
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker {}: {}", name, event.getStateTransition()));
        return breaker;
    }
}
