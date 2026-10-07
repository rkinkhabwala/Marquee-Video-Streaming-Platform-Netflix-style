package com.marquee.api.recsys;

import java.util.List;

/** The recommendation system's HTTP APIs. Implementations throw {@link RecsysUnavailableException} on failure. */
public interface RecsysClient {
    /** @return how many events recsys accepted (the rest were rejected as invalid and must not be retried) */
    int sendEvents(List<RecsysEvent> events);

    void upsertCatalog(List<CatalogItem> items);

    void deleteCatalogItem(String itemId);

    void deleteUserData(String userId);

    /**
     * @param surface {@code home} for personal picks, {@code related} with a seed item
     * @param explicitAllowed false for kids profiles
     */
    List<String> recommend(String userId, String surface, String seedItemId, int limit, boolean explicitAllowed);

    class RecsysUnavailableException extends RuntimeException {
        public RecsysUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
