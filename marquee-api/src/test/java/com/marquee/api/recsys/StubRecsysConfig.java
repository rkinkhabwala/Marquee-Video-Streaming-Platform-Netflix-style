package com.marquee.api.recsys;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class StubRecsysConfig {
    @Bean
    @Primary
    public StubRecsysClient stubRecsysClient() {
        return new StubRecsysClient();
    }
}
