package com.marquee.api.recsys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Runs the real HTTP client against an in-process HTTP server standing in for recsys. */
class HttpRecsysClientTest {
    record Received(String method, String path, String query, String apiKey, String body) {
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile long delayMs = 0;
    private final AtomicInteger calls = new AtomicInteger();
    private HttpRecsysClient client;
    private CircuitBreaker serving;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        RecsysProperties properties = new RecsysProperties(true, base, base, base, "test-key",
                Duration.ofMillis(300), Duration.ofMillis(300), false, 10);
        serving = RecsysConfig.breaker("serving-test");
        client = new HttpRecsysClient(properties, Jackson2ObjectMapperBuilder.json().build(), serving, RecsysConfig.breaker("delivery-test"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("X-Api-Key"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void requestsRecommendationsWithApiKeyAndReturnsItemIdsInOrder() {
        responseBody = """
                {"recommendationId":"r1","items":[{"itemId":"mq-t-2","position":0},{"itemId":"v_000001","position":1}]}
                """;

        List<String> items = client.recommend("mq-p-5", "related", "mq-t-9", 50, false);

        assertThat(items).containsExactly("mq-t-2", "v_000001");
        Received request = received.get(0);
        assertThat(request.path()).isEqualTo("/v1/recommendations");
        assertThat(request.apiKey()).isEqualTo("test-key");
        assertThat(request.query()).contains("userId=mq-p-5", "domain=video", "context=related", "limit=50", "explicit=false", "seedItemId=mq-t-9");
    }

    @Test
    void sendsEventsAsRecsysJson() {
        responseBody = "{\"accepted\":1,\"rejected\":[]}";
        RecsysEvent event = EngagementEventMapper.map(EngagementType.COMPLETED, 5L, 9L, 600, 600, Instant.parse("2026-10-07T18:30:00Z"));

        assertThat(client.sendEvents(List.of(event))).isEqualTo(1);

        Received request = received.get(0);
        assertThat(request.path()).isEqualTo("/v1/events");
        assertThat(request.body())
                .contains("\"events\":[{", "\"userId\":\"mq-p-5\"", "\"itemId\":\"mq-t-9\"", "\"eventType\":\"PLAY_END\"", "\"eventTs\":\"2026-10-07T18:30:00Z\"")
                .doesNotContain("searchQueryId", "null");
    }

    @Test
    void timeoutsBecomeUnavailableAndOpenTheBreaker() {
        delayMs = 1000;
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.recommend("mq-p-1", "home", null, 10, true))
                    .isInstanceOf(RecsysClient.RecsysUnavailableException.class);
        }
        assertThat(serving.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = calls.get();
        assertThatThrownBy(() -> client.recommend("mq-p-1", "home", null, 10, true))
                .isInstanceOf(RecsysClient.RecsysUnavailableException.class)
                .hasMessageContaining("is open");
        assertThat(calls.get()).as("open breaker short-circuits without calling recsys").isEqualTo(before);
    }

    @Test
    void clientErrorsDoNotOpenTheBreaker() {
        status = 400;
        responseBody = "{\"error\":\"INVALID_CONTEXT\"}";
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> client.recommend("mq-p-1", "home", null, 10, true))
                    .isInstanceOf(RecsysClient.RecsysUnavailableException.class)
                    .hasMessageContaining("400");
        }
        assertThat(serving.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void deletingAnUnknownCatalogItemIsNotAnError() {
        status = 404;
        client.deleteCatalogItem("mq-t-404");
        assertThat(received.get(0).method()).isEqualTo("DELETE");
        assertThat(received.get(0).path()).isEqualTo("/v1/catalog/items/mq-t-404");
    }
}
