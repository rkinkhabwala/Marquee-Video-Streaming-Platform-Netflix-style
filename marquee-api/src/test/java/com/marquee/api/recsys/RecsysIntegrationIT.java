package com.marquee.api.recsys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marquee.api.IntegrationTestSupport;
import com.marquee.api.catalog.CatalogService;
import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.GenreRepository;
import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleService;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.UpdateTitleRequest;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.playback.PlaybackService;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.profile.ProfileService;
import com.marquee.api.progress.ProgressService;
import com.marquee.api.security.JwtService;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Transactional
class RecsysIntegrationIT extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private StubRecsysClient recsys;
    @Autowired private RecsysOutboxRelay relay;
    @Autowired private OutboxRepository outbox;
    @Autowired private ProgressService progressService;
    @Autowired private PlaybackService playbackService;
    @Autowired private CatalogService catalogService;
    @Autowired private TitleService titleService;
    @Autowired private ProfileService profileService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private TitleRepository titleRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private VideoAssetRepository videoAssetRepository;

    private User user;
    private Profile profile;

    @BeforeEach
    void setUp() {
        recsys.reset();
        outbox.deleteAll();
        user = userRepository.save(new User("recs-" + System.nanoTime() + "@example.com", "hash"));
        profile = profileRepository.save(new Profile(user, "Sam", null, false));
    }

    @Test
    void watchingRecordsPlayStartAndEachMilestoneOnceThenDelivers() {
        Title movie = title("Ocean Life", MaturityRating.PG, true);
        VideoAsset asset = asset(movie, 600);

        playbackService.getPlayback(user.getId(), profile.getId(), asset.getId());
        progressService.saveProgress(user.getId(), profile.getId(), asset.getId(), 160); // 26%
        progressService.saveProgress(user.getId(), profile.getId(), asset.getId(), 100); // seek back
        progressService.saveProgress(user.getId(), profile.getId(), asset.getId(), 500); // 83%: 50 and 75
        progressService.saveProgress(user.getId(), profile.getId(), asset.getId(), 590); // completed

        relay.relayOnce();

        assertThat(recsys.events).extracting(RecsysEvent::eventType)
                .containsExactly("PLAY_START", "PLAY_END", "DWELL", "DWELL", "PLAY_END");
        assertThat(recsys.events).allSatisfy(event -> {
            assertThat(event.userId()).isEqualTo("mq-p-" + profile.getId());
            assertThat(event.itemId()).isEqualTo("mq-t-" + movie.getId());
        });
        assertThat(recsys.events.get(4).value()).isEqualTo(600.0);
        assertThat(outbox.findByKindOrderByIdAsc(OutboxEntry.Kind.EVENT)).isEmpty();
    }

    @Test
    void explicitFeedbackListAndSearchAreRecordedWithoutQueryText() {
        Title movie = title("Deep Sea", MaturityRating.G, true);

        progressService.rate(user.getId(), profile.getId(), movie.getId(), 1);
        progressService.rate(user.getId(), profile.getId(), movie.getId(), 1); // unchanged: no event
        progressService.rate(user.getId(), profile.getId(), movie.getId(), -1);
        progressService.addToMyList(user.getId(), profile.getId(), movie.getId());
        progressService.addToMyList(user.getId(), profile.getId(), movie.getId()); // already there
        catalogService.search(user.getId(), profile.getId(), "secret diary", 0, 20);
        catalogService.search(user.getId(), profile.getId(), "secret diary", 1, 20); // later pages: not a new search

        List<OutboxEntry> entries = outbox.findByKindOrderByIdAsc(OutboxEntry.Kind.EVENT);
        assertThat(entries).extracting(OutboxEntry::getPayload).noneMatch(payload -> payload.contains("secret"));
        relay.relayOnce();
        assertThat(recsys.events).extracting(RecsysEvent::eventType).containsExactly("LIKE", "DISLIKE", "SAVE", "SEARCH");
    }

    @Test
    void outboxKeepsEntriesWhileRecsysIsDownAndDeliversLater() {
        Title movie = title("Deep Sea", MaturityRating.G, true);
        progressService.addToMyList(user.getId(), profile.getId(), movie.getId());

        recsys.failure = new RecsysClient.RecsysUnavailableException("connection refused", null);
        assertThat(relay.relayOnce()).isZero();
        List<OutboxEntry> waiting = outbox.findByKindOrderByIdAsc(OutboxEntry.Kind.EVENT);
        assertThat(waiting).hasSize(1);
        assertThat(waiting.get(0).getAttempts()).isEqualTo(1);

        recsys.failure = null;
        relay.relayOnce();
        assertThat(recsys.events).extracting(RecsysEvent::eventType).containsExactly("SAVE");
        assertThat(outbox.findByKindOrderByIdAsc(OutboxEntry.Kind.EVENT)).isEmpty();
    }

    @Test
    void permanentlyRejectedBatchesAreDroppedInsteadOfRetriedForever() {
        Title movie = title("Deep Sea", MaturityRating.G, true);
        progressService.addToMyList(user.getId(), profile.getId(), movie.getId());

        recsys.failure = new RecsysClient.RecsysUnavailableException("bad request",
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));
        relay.relayOnce();

        assertThat(outbox.findByKindOrderByIdAsc(OutboxEntry.Kind.EVENT)).isEmpty();
    }

    @Test
    void publishedTitlesAreSyncedToTheCatalogAndUnpublishedOnesRemoved() {
        Genre genre = genreRepository.save(new Genre("Documentary " + System.nanoTime()));
        Title movie = title("Ocean Life", MaturityRating.R, false, genre);
        asset(movie, 600);

        titleService.publishTitle(movie.getId());
        relay.relayOnce();

        assertThat(recsys.upserts).hasSize(1);
        CatalogItem item = recsys.upserts.get(0);
        assertThat(item.itemId()).isEqualTo("mq-t-" + movie.getId());
        assertThat(item.domain()).isEqualTo("video");
        assertThat(item.genres()).containsExactly(genre.getName().toLowerCase());
        assertThat(item.durationMs()).isEqualTo(600_000L);
        assertThat(item.explicit()).isTrue();

        titleService.updateTitle(movie.getId(), new UpdateTitleRequest(null, null, null, null, null, null, null, null, false));
        relay.relayOnce();
        assertThat(recsys.deletedItems).containsExactly("mq-t-" + movie.getId());
    }

    @Test
    void homeShowsHydratedRecommendationsAndBecauseYouWatched() throws Exception {
        Title watched = title("Ocean Life", MaturityRating.PG, true);
        Title pick = title("Deep Sea", MaturityRating.G, true);
        Title hidden = title("Unreleased", MaturityRating.G, false);
        progressService.saveProgress(user.getId(), profile.getId(), asset(watched, 600).getId(), 120);
        recsys.recommendations = request -> List.of("v_000001", "mq-t-" + hidden.getId(), "mq-t-" + pick.getId(), "mq-t-" + watched.getId());

        mockMvc.perform(get("/api/home").header("Authorization", bearer()).header("X-Profile-Id", profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].title").value("Top picks for Sam"))
                .andExpect(jsonPath("$[1].items.length()").value(2))
                .andExpect(jsonPath("$[1].items[0].id").value(pick.getId()))
                .andExpect(jsonPath("$[2].title").value("Because you watched Ocean Life"))
                .andExpect(jsonPath("$[2].items.length()").value(1))
                .andExpect(jsonPath("$[2].items[0].id").value(pick.getId()));

        assertThat(recsys.recommendRequests).contains(
                new StubRecsysClient.Recommend("mq-p-" + profile.getId(), "home", null, true),
                new StubRecsysClient.Recommend("mq-p-" + profile.getId(), "related", "mq-t-" + watched.getId(), true));
    }

    @Test
    void homeStillWorksWithTrendingWhenRecsysIsDown() throws Exception {
        Title watched = title("Ocean Life", MaturityRating.PG, true);
        progressService.saveProgress(user.getId(), profile.getId(), asset(watched, 600).getId(), 120);
        recsys.failure = new RecsysClient.RecsysUnavailableException("circuit breaker recsys-serving is open", null);

        mockMvc.perform(get("/api/home").header("Authorization", bearer()).header("X-Profile-Id", profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].title").value("Top picks for Sam"))
                .andExpect(jsonPath("$[1].items[0].id").value(watched.getId()))
                .andExpect(jsonPath("$[2].title").value("My List"));
    }

    @Test
    void deletingAProfileErasesItsRecsysData() {
        Profile extra = profileRepository.save(new Profile(user, "Temp", null, false));

        profileService.deleteProfile(user.getId(), extra.getId());
        relay.relayOnce();

        assertThat(recsys.deletedUsers).containsExactly("mq-p-" + extra.getId());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private Title title(String name, MaturityRating rating, boolean published, Genre... genres) {
        Title title = new Title(TitleType.MOVIE, name, "About " + name, 2024, rating);
        title.setPublished(published);
        title.getGenres().addAll(List.of(genres));
        return titleRepository.saveAndFlush(title);
    }

    private VideoAsset asset(Title title, int seconds) {
        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.READY);
        asset.setDurationSeconds(seconds);
        return videoAssetRepository.saveAndFlush(asset);
    }
}
