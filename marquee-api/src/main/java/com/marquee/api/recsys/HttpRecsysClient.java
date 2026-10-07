package com.marquee.api.recsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * recsys over HTTP. Serving calls (recommendations) and delivery calls (events, catalog) have
 * separate circuit breakers, so an ingest backlog never trips the home page's breaker or vice versa.
 */
public class HttpRecsysClient implements RecsysClient {
    private static final String API_KEY_HEADER = "X-Api-Key";

    private final RestClient restClient;
    private final RecsysProperties properties;
    private final CircuitBreaker servingBreaker;
    private final CircuitBreaker deliveryBreaker;

    public HttpRecsysClient(RecsysProperties properties, ObjectMapper objectMapper, CircuitBreaker servingBreaker, CircuitBreaker deliveryBreaker) {
        this.properties = properties;
        this.servingBreaker = servingBreaker;
        this.deliveryBreaker = deliveryBreaker;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                // Spring's ObjectMapper writes Instants as ISO-8601, which is what recsys expects.
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    record EventBatch(List<RecsysEvent> events) {
    }

    record EventBatchResponse(int accepted, List<Map<String, Object>> rejected) {
    }

    record CatalogBatch(List<CatalogItem> items) {
    }

    record RecommendationResponse(List<Item> items) {
        record Item(String itemId) {
        }
    }

    @Override
    public int sendEvents(List<RecsysEvent> events) {
        EventBatchResponse response = call(deliveryBreaker, () -> restClient.post()
                .uri(properties.ingestUrl() + "/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EventBatch(events))
                .retrieve()
                .body(EventBatchResponse.class));
        return response == null ? 0 : response.accepted();
    }

    @Override
    public void upsertCatalog(List<CatalogItem> items) {
        call(deliveryBreaker, () -> restClient.post()
                .uri(properties.catalogUrl() + "/v1/catalog/items")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CatalogBatch(items))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void deleteCatalogItem(String itemId) {
        call(deliveryBreaker, () -> {
            try {
                return restClient.delete().uri(properties.catalogUrl() + "/v1/catalog/items/{id}", itemId).retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.NotFound alreadyGone) {
                return null;
            }
        });
    }

    @Override
    public void deleteUserData(String userId) {
        call(deliveryBreaker, () -> restClient.delete()
                .uri(properties.ingestUrl() + "/v1/users/{id}/data", userId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public List<String> recommend(String userId, String surface, String seedItemId, int limit, boolean explicitAllowed) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.recommendationsUrl() + "/v1/recommendations")
                .queryParam("userId", userId)
                .queryParam("domain", EngagementEventMapper.DOMAIN)
                .queryParam("context", surface)
                .queryParam("limit", limit)
                .queryParam("device", "WEB")
                .queryParam("explicit", explicitAllowed);
        if (seedItemId != null) {
            uri.queryParam("seedItemId", seedItemId);
        }
        RecommendationResponse response = call(servingBreaker, () -> restClient.get()
                .uri(uri.build().toUri())
                .retrieve()
                .body(RecommendationResponse.class));
        return response == null || response.items() == null
                ? List.of()
                : response.items().stream().map(RecommendationResponse.Item::itemId).toList();
    }

    private <T> T call(CircuitBreaker breaker, Supplier<T> request) {
        try {
            return breaker.executeSupplier(request);
        } catch (CallNotPermittedException e) {
            throw new RecsysUnavailableException("recsys circuit breaker " + breaker.getName() + " is open", e);
        } catch (HttpClientErrorException e) {
            throw new RecsysUnavailableException("recsys rejected the request: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            // Includes RestClientException and the CancellationException the JDK client throws on a read timeout.
            throw new RecsysUnavailableException("recsys call failed: " + e, e);
        }
    }

    static boolean isClientError(Throwable error) {
        return error instanceof HttpClientErrorException e && e.getStatusCode().is4xxClientError()
                && e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS;
    }
}
