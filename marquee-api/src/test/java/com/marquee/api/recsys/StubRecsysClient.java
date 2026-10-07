package com.marquee.api.recsys;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** In-memory recsys for integration tests: records what Marquee sends and returns scripted recommendations. */
public class StubRecsysClient implements RecsysClient {
    public record Recommend(String userId, String surface, String seedItemId, boolean explicitAllowed) {
    }

    public final List<RecsysEvent> events = new CopyOnWriteArrayList<>();
    public final List<CatalogItem> upserts = new CopyOnWriteArrayList<>();
    public final List<String> deletedItems = new CopyOnWriteArrayList<>();
    public final List<String> deletedUsers = new CopyOnWriteArrayList<>();
    public final List<Recommend> recommendRequests = new CopyOnWriteArrayList<>();
    public volatile Function<Recommend, List<String>> recommendations = request -> List.of();
    /** When set, every call throws it (recsys down, or rejecting). */
    public volatile RuntimeException failure;

    public void reset() {
        events.clear();
        upserts.clear();
        deletedItems.clear();
        deletedUsers.clear();
        recommendRequests.clear();
        recommendations = request -> List.of();
        failure = null;
    }

    private void maybeFail() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public int sendEvents(List<RecsysEvent> batch) {
        maybeFail();
        events.addAll(batch);
        return batch.size();
    }

    @Override
    public void upsertCatalog(List<CatalogItem> items) {
        maybeFail();
        upserts.addAll(items);
    }

    @Override
    public void deleteCatalogItem(String itemId) {
        maybeFail();
        deletedItems.add(itemId);
    }

    @Override
    public void deleteUserData(String userId) {
        maybeFail();
        deletedUsers.add(userId);
    }

    @Override
    public List<String> recommend(String userId, String surface, String seedItemId, int limit, boolean explicitAllowed) {
        Recommend request = new Recommend(userId, surface, seedItemId, explicitAllowed);
        recommendRequests.add(request);
        maybeFail();
        return recommendations.apply(request);
    }
}
