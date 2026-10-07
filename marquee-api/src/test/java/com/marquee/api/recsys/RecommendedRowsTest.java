package com.marquee.api.recsys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.profile.Profile;
import com.marquee.api.user.User;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendedRowsTest {
    private final TitleRepository titles = mock(TitleRepository.class);
    private final FakeClient client = new FakeClient();
    private final RecsysProperties properties = new RecsysProperties(true, "", "", "", "", Duration.ofMillis(100), Duration.ofMillis(100), false, 10);
    private final RecommendedRows rows = new RecommendedRows(client, titles, properties);

    private final Map<Long, Title> catalog = Map.of(
            1L, title(1, "Ocean Life", MaturityRating.PG, true),
            2L, title(2, "Deep Sea", MaturityRating.G, true),
            3L, title(3, "Night Crime", MaturityRating.R, true),
            4L, title(4, "Unreleased", MaturityRating.G, false));
    private final List<Title> trending = List.of(title(9, "Trending Hit", MaturityRating.G, true));

    @BeforeEach
    void catalog() {
        when(titles.findAllById(anyIterable())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).map(catalog::get).filter(t -> t != null).toList();
        });
    }

    @Test
    void topPicksKeepRecsysOrderAndDropUnknownOrHiddenTitles() {
        client.recommendations = request -> List.of("v_000001", "mq-t-2", "mq-t-3", "mq-t-4", "mq-t-1", "mq-t-2");

        var result = rows.build(profile(5, "Sam", false), null, trending);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Top picks for Sam");
        assertThat(result.get(0).titles()).extracting(Title::getName).containsExactly("Deep Sea", "Night Crime", "Ocean Life");
        assertThat(client.requests).containsExactly(new FakeClient.Request("mq-p-5", "home", null, true));
    }

    @Test
    void kidsProfilesGetOnlyKidsSafeTitlesAndNoExplicitItems() {
        client.recommendations = request -> List.of("mq-t-3", "mq-t-1");

        var result = rows.build(profile(6, "Kid", true), null, trending);

        assertThat(result.get(0).titles()).extracting(Title::getName).containsExactly("Ocean Life");
        assertThat(client.requests.get(0).explicitAllowed()).isFalse();
    }

    @Test
    void becauseYouWatchedUsesTheLastWatchedTitleAsSeedAndExcludesIt() {
        Title watched = catalog.get(1L);
        client.recommendations = request -> "related".equals(request.surface()) ? List.of("mq-t-1", "mq-t-2") : List.of("mq-t-2");

        var result = rows.build(profile(5, "Sam", false), watched, trending);

        assertThat(result).extracting(RecommendedRows.Row::title).containsExactly("Top picks for Sam", "Because you watched Ocean Life");
        assertThat(result.get(1).titles()).extracting(Title::getName).containsExactly("Deep Sea");
        assertThat(client.requests).contains(new FakeClient.Request("mq-p-5", "related", "mq-t-1", true));
    }

    @Test
    void fallsBackToTrendingWhenRecsysFailsOrHasNothingUsable() {
        client.recommendations = request -> {
            throw new RecsysClient.RecsysUnavailableException("down", null);
        };
        var failed = rows.build(profile(5, "Sam", false), catalog.get(1L), trending);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).titles()).isEqualTo(trending);

        client.recommendations = request -> List.of("v_000001", "v_000002");
        assertThat(rows.build(profile(5, "Sam", false), null, trending).get(0).titles()).isEqualTo(trending);
    }

    @Test
    void disabledRecsysIsNeverCalled() {
        var disabled = new RecommendedRows(client, titles, new RecsysProperties(false, "", "", "", "", Duration.ZERO, Duration.ZERO, false, 10));

        assertThat(disabled.build(profile(5, "Sam", false), catalog.get(1L), trending).get(0).titles()).isEqualTo(trending);
        assertThat(client.requests).isEmpty();
    }

    static class FakeClient implements RecsysClient {
        record Request(String userId, String surface, String seed, boolean explicitAllowed) {
        }

        final List<Request> requests = new CopyOnWriteArrayList<>();
        Function<Request, List<String>> recommendations = request -> List.of();

        @Override
        public List<String> recommend(String userId, String surface, String seedItemId, int limit, boolean explicitAllowed) {
            Request request = new Request(userId, surface, seedItemId, explicitAllowed);
            requests.add(request);
            return recommendations.apply(request);
        }

        @Override
        public int sendEvents(List<RecsysEvent> events) {
            return events.size();
        }

        @Override
        public void upsertCatalog(List<CatalogItem> items) {
        }

        @Override
        public void deleteCatalogItem(String itemId) {
        }

        @Override
        public void deleteUserData(String userId) {
        }
    }

    private static Title title(long id, String name, MaturityRating rating, boolean published) {
        Title title = new Title(TitleType.MOVIE, name, null, 2024, rating);
        title.setPublished(published);
        setId(title, id);
        return title;
    }

    private static Profile profile(long id, String name, boolean kids) {
        Profile profile = new Profile(new User("u@example.com", "hash"), name, null, kids);
        setId(profile, id);
        return profile;
    }

    private static void setId(Object target, long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
