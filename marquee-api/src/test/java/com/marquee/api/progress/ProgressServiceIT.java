package com.marquee.api.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marquee.api.catalog.CreateEpisodeRequest;
import com.marquee.api.catalog.EpisodeResponse;
import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.GenreRepository;
import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Season;
import com.marquee.api.catalog.SeasonRepository;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleService;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.security.JwtService;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ProgressServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ProgressService progressService;
    @Autowired private TitleService titleService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private TitleRepository titleRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private VideoAssetRepository videoAssetRepository;
    @Autowired private WatchProgressRepository watchProgressRepository;

    private User user;
    private Profile adult;
    private Profile kid;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("viewer-" + System.nanoTime() + "@example.com", "hash"));
        adult = profileRepository.save(new Profile(user, "Adult", null, false));
        kid = profileRepository.save(new Profile(user, "Kid", null, true));
    }

    @Test
    void savedProgressShowsInContinueWatchingWithResumePoint() throws Exception {
        Title movie = title(TitleType.MOVIE, "Big Buck Bunny", MaturityRating.G, true);
        VideoAsset asset = asset(movie, 6012);
        String token = "Bearer " + jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/profiles/{profileId}/progress/{assetId}", adult.getId(), asset.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\":1325}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(get("/api/home")
                        .header("Authorization", token)
                        .header("X-Profile-Id", adult.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Continue Watching"))
                .andExpect(jsonPath("$[0].items[0].id").value(movie.getId()))
                .andExpect(jsonPath("$[0].items[0].resumeAt").value(1325))
                .andExpect(jsonPath("$[0].items[0].resumeAssetId").value(asset.getId()));
    }

    @Test
    void progressAtNinetyFivePercentCompletesAndLeavesContinueWatching() {
        Title movie = title(TitleType.MOVIE, "Short", MaturityRating.PG, true);
        VideoAsset asset = asset(movie, 600);

        assertThat(progressService.saveProgress(user.getId(), adult.getId(), asset.getId(), 569).completed()).isFalse();
        assertThat(row(adult, "Continue Watching")).extracting(TitleCardResponse::id).containsExactly(movie.getId());

        assertThat(progressService.saveProgress(user.getId(), adult.getId(), asset.getId(), 570).completed()).isTrue();
        assertThat(row(adult, "Continue Watching")).isEmpty();
    }

    @Test
    void seriesAppearsOnceInContinueWatchingAtLatestEpisode() {
        Title series = title(TitleType.SERIES, "Show", MaturityRating.TV_PG, true);
        VideoAsset ep1 = asset(series, 1200);
        VideoAsset ep2 = asset(series, 1200);

        progressService.saveProgress(user.getId(), adult.getId(), ep1.getId(), 300);
        watchProgressRepository.findByProfile_IdAndVideoAsset_Id(adult.getId(), ep1.getId()).orElseThrow()
                .setUpdatedAt(Instant.now().minusSeconds(60));
        progressService.saveProgress(user.getId(), adult.getId(), ep2.getId(), 45);

        List<TitleCardResponse> continueWatching = row(adult, "Continue Watching");
        assertThat(continueWatching).hasSize(1);
        assertThat(continueWatching.get(0).resumeAssetId()).isEqualTo(ep2.getId());
        assertThat(continueWatching.get(0).resumeAt()).isEqualTo(45);
    }

    @Test
    void kidsProfileOnlySeesKidsSafePublishedTitles() {
        Genre genre = genreRepository.save(new Genre("Genre-" + System.nanoTime()));
        Title kidsSafe = title(TitleType.MOVIE, "Kids Safe", MaturityRating.PG, true, genre);
        Title mature = title(TitleType.MOVIE, "Mature", MaturityRating.R, true, genre);
        Title unrated = title(TitleType.MOVIE, "Unrated", null, true, genre);
        Title draft = title(TitleType.MOVIE, "Draft", MaturityRating.G, false, genre);
        for (Title t : List.of(kidsSafe, mature, unrated, draft)) {
            progressService.saveProgress(user.getId(), adult.getId(), asset(t, 600).getId(), 60);
        }

        Map<String, List<Long>> kidRows = rowIds(kid);
        assertThat(kidRows.get("Trending")).containsExactly(kidsSafe.getId());
        assertThat(kidRows.get("New Releases")).containsExactly(kidsSafe.getId());
        assertThat(kidRows.get(genre.getName())).containsExactly(kidsSafe.getId());

        Map<String, List<Long>> adultRows = rowIds(adult);
        assertThat(adultRows.get("Continue Watching")).containsExactlyInAnyOrder(kidsSafe.getId(), mature.getId(), unrated.getId());
        assertThat(adultRows.get("New Releases")).doesNotContain(draft.getId());

        assertStatus(() -> progressService.addToMyList(user.getId(), kid.getId(), mature.getId()), HttpStatus.FORBIDDEN);
        assertStatus(() -> progressService.addToMyList(user.getId(), adult.getId(), draft.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void trendingRanksByDistinctViewers() {
        Title popular = title(TitleType.MOVIE, "Popular", MaturityRating.G, true);
        Title niche = title(TitleType.MOVIE, "Niche", MaturityRating.G, true);
        VideoAsset popularAsset = asset(popular, 600);
        VideoAsset nicheAsset = asset(niche, 600);

        progressService.saveProgress(user.getId(), adult.getId(), popularAsset.getId(), 10);
        progressService.saveProgress(user.getId(), kid.getId(), popularAsset.getId(), 10);
        progressService.saveProgress(user.getId(), adult.getId(), nicheAsset.getId(), 10);

        assertThat(rowIds(adult).get("Trending")).containsExactly(popular.getId(), niche.getId());
    }

    @Test
    void nextEpisodeFollowsLastWatchedEpisode() {
        Title series = title(TitleType.SERIES, "Serial", MaturityRating.TV_PG, true);
        Season s1 = seasonRepository.save(new Season(series, 1, "Season 1"));
        Season s2 = seasonRepository.save(new Season(series, 2, "Season 2"));
        VideoAsset a1 = asset(series, 1200);
        VideoAsset a2 = asset(series, 1200);
        EpisodeResponse e1 = titleService.createEpisode(s1.getId(), new CreateEpisodeRequest(1, "Pilot", null, a1.getId()));
        EpisodeResponse e2 = titleService.createEpisode(s2.getId(), new CreateEpisodeRequest(1, "Return", null, a2.getId()));

        assertThat(progressService.getNextEpisode(user.getId(), adult.getId(), series.getId()).episodeId()).isEqualTo(e1.id());

        progressService.saveProgress(user.getId(), adult.getId(), a1.getId(), 1190);
        NextEpisodeResponse next = progressService.getNextEpisode(user.getId(), adult.getId(), series.getId());
        assertThat(next.episodeId()).isEqualTo(e2.id());
        assertThat(next.seasonNumber()).isEqualTo(2);
        assertThat(next.videoAssetId()).isEqualTo(a2.getId());

        progressService.saveProgress(user.getId(), adult.getId(), a2.getId(), 10);
        assertStatus(() -> progressService.getNextEpisode(user.getId(), adult.getId(), series.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void episodeCannotUseAnotherTitlesAsset() {
        Title series = title(TitleType.SERIES, "Serial", MaturityRating.TV_PG, true);
        Title other = title(TitleType.MOVIE, "Other", MaturityRating.G, true);
        Season season = seasonRepository.save(new Season(series, 1, "Season 1"));

        assertStatus(() -> titleService.createEpisode(season.getId(),
                new CreateEpisodeRequest(1, "Pilot", null, asset(other, 600).getId())), HttpStatus.BAD_REQUEST);
    }

    private Title title(TitleType type, String name, MaturityRating rating, boolean published, Genre... genres) {
        Title title = new Title(type, name, null, 2024, rating);
        title.setPublished(published);
        title.getGenres().addAll(List.of(genres));
        return titleRepository.save(title);
    }

    private VideoAsset asset(Title title, int durationSeconds) {
        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.READY);
        asset.setDurationSeconds(durationSeconds);
        return videoAssetRepository.save(asset);
    }

    private List<TitleCardResponse> row(Profile profile, String name) {
        return progressService.getHome(user.getId(), profile.getId()).stream()
                .filter(row -> row.title().equals(name))
                .findFirst()
                .orElseThrow()
                .items();
    }

    private Map<String, List<Long>> rowIds(Profile profile) {
        return progressService.getHome(user.getId(), profile.getId()).stream()
                .collect(Collectors.toMap(HomeRowResponse::title,
                        row -> row.items().stream().map(TitleCardResponse::id).toList()));
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}
