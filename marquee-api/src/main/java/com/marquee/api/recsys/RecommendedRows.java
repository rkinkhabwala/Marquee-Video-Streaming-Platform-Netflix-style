package com.marquee.api.recsys;

import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.profile.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The personalised home rows: "Top picks for {profile}" and "Because you watched {title}", both from
 * recsys. Results are hydrated from Marquee's own catalog, which drops ids Marquee does not know
 * (recsys pads short lists with its static fallback) and applies Marquee's visibility rules
 * (published only, kids ratings). If recsys is unavailable or has nothing usable, "Top picks" falls
 * back to Trending and "Because you watched" is left out.
 */
@Component
public class RecommendedRows {
    private static final Logger log = LoggerFactory.getLogger(RecommendedRows.class);
    /** Ask for more than we show: some results will be filtered out by hydration. */
    static final int FETCH = 50;
    static final int ROW_SIZE = 10;

    private final RecsysClient client;
    private final TitleRepository titleRepository;
    private final RecsysProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RecommendedRows(RecsysClient client, TitleRepository titleRepository, RecsysProperties properties) {
        this.client = client;
        this.titleRepository = titleRepository;
        this.properties = properties;
    }

    public record Row(String title, List<Title> titles) {
    }

    /**
     * @param lastWatched the most recently watched visible title, or null
     * @param trending fallback for "Top picks"
     */
    public List<Row> build(Profile profile, Title lastWatched, List<Title> trending) {
        String userId = RecsysIds.user(profile.getId());
        boolean explicitAllowed = !profile.isKids();

        CompletableFuture<List<String>> picks = fetch(() -> client.recommend(userId, "home", null, FETCH, explicitAllowed));
        CompletableFuture<List<String>> related = lastWatched == null
                ? CompletableFuture.completedFuture(List.of())
                : fetch(() -> client.recommend(userId, "related", RecsysIds.item(lastWatched.getId()), FETCH, explicitAllowed));

        List<Row> rows = new ArrayList<>();
        List<Title> topPicks = hydrate(picks.join(), profile, Set.of());
        rows.add(new Row("Top picks for " + profile.getName(), topPicks.isEmpty() ? trending : topPicks));
        if (lastWatched != null) {
            List<Title> because = hydrate(related.join(), profile, Set.of(lastWatched.getId()));
            if (!because.isEmpty()) {
                rows.add(new Row("Because you watched " + lastWatched.getName(), because));
            }
        }
        return rows;
    }

    private CompletableFuture<List<String>> fetch(Supplier<List<String>> call) {
        if (!properties.enabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(call, executor).exceptionally(error -> {
            log.debug("recsys recommendations unavailable: {}", error.getMessage());
            return List.of();
        });
    }

    private List<Title> hydrate(List<String> itemIds, Profile profile, Set<Long> exclude) {
        List<Long> ids = itemIds.stream()
                .map(RecsysIds::titleId)
                .flatMap(Optional::stream)
                .filter(id -> !exclude.contains(id))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Title> byId = titleRepository.findAllById(ids).stream().collect(Collectors.toMap(Title::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(title -> title != null && visibleTo(title, profile))
                .limit(ROW_SIZE)
                .toList();
    }

    static boolean visibleTo(Title title, Profile profile) {
        return title.isPublished()
                && (!profile.isKids() || (title.getMaturityRating() != null && title.getMaturityRating().isKidsSafe()));
    }
}
