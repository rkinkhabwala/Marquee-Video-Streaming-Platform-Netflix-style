package com.marquee.api.recsys;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to the external recommendation system (recsys). When {@code enabled} is false, no
 * events are recorded and "Top picks" falls back to Trending.
 */
@ConfigurationProperties("app.recsys")
public record RecsysProperties(
        boolean enabled,
        String ingestUrl,
        String catalogUrl,
        String recommendationsUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        boolean relayEnabled,
        int relayBatchSize) {
}
