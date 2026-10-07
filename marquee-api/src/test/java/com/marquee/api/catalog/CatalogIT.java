package com.marquee.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marquee.api.IntegrationTestSupport;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.security.JwtService;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CatalogIT extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private TitleRepository titleRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private VideoAssetRepository videoAssetRepository;

    private String bearer;
    private Profile adult;
    private Profile kid;
    private Genre genre;
    private String tag;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("catalog-" + System.nanoTime() + "@example.com", "hash"));
        bearer = "Bearer " + jwtService.generateAccessToken(user);
        adult = profileRepository.save(new Profile(user, "Adult", null, false));
        kid = profileRepository.save(new Profile(user, "Kid", null, true));
        tag = "zq" + System.nanoTime();
        genre = genreRepository.save(new Genre("Genre " + tag));
    }

    @Test
    void searchMatchesNameAndSynopsisWithKidsAndPublishedFilters() throws Exception {
        Title robots = title(TitleType.MOVIE, "Robots of " + tag, "Machines rebel", MaturityRating.PG, true);
        Title heist = title(TitleType.MOVIE, "Heist", "A crew of " + tag + " thieves", MaturityRating.R, true);
        title(TitleType.MOVIE, "Draft " + tag, null, MaturityRating.G, false);

        as(adult, get("/api/search").param("q", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(robots.getId()))
                .andExpect(jsonPath("$.content[1].id").value(heist.getId()));
        as(kid, get("/api/search").param("q", tag))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(robots.getId()));
        as(adult, get("/api/search").param("q", "robot " + tag))
                .andExpect(jsonPath("$.content[0].id").value(robots.getId()));
    }

    @Test
    void browseFiltersByGenreAndTypeWithPagination() throws Exception {
        Title movie = title(TitleType.MOVIE, "Movie " + tag, null, MaturityRating.G, true, genre);
        Title series = title(TitleType.SERIES, "Series " + tag, null, MaturityRating.G, true, genre);

        as(adult, get("/api/titles").param("genre", genre.getName().toUpperCase()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(series.getId()));
        as(adult, get("/api/titles").param("genre", genre.getName()).param("type", "MOVIE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(movie.getId()));
        as(adult, get("/api/titles").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void titleDetailIncludesSeasonsEpisodesRatingAndMyList() throws Exception {
        Title series = title(TitleType.SERIES, "Show " + tag, "desc", MaturityRating.TV_PG, true, genre);
        Season season = seasonRepository.save(new Season(series, 1, "Season 1"));
        VideoAsset asset = new VideoAsset(series, VideoAssetStatus.READY);
        asset.setDurationSeconds(1200);
        asset = videoAssetRepository.save(asset);
        episodeRepository.save(new Episode(season, 1, "Pilot", null, asset));
        episodeRepository.save(new Episode(season, 2, "Next", null, null));

        as(adult, put("/api/ratings/" + series.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}"))
                .andExpect(status().isNoContent());
        as(adult, put("/api/my-list/" + series.getId())).andExpect(status().isNoContent());

        as(adult, get("/api/titles/" + series.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.inMyList").value(true))
                .andExpect(jsonPath("$.genres[0]").value(genre.getName()))
                .andExpect(jsonPath("$.seasons[0].episodes.length()").value(2))
                .andExpect(jsonPath("$.seasons[0].episodes[0].videoAssetId").value(asset.getId()))
                .andExpect(jsonPath("$.seasons[0].episodes[0].playable").value(true))
                .andExpect(jsonPath("$.seasons[0].episodes[1].playable").value(false));

        as(adult, put("/api/ratings/" + series.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movieDetailExposesReadyAssetAndHidesUnpublished() throws Exception {
        Title movie = title(TitleType.MOVIE, "Film " + tag, null, MaturityRating.PG, true);
        VideoAsset ready = new VideoAsset(movie, VideoAssetStatus.READY);
        ready.setDurationSeconds(90);
        ready = videoAssetRepository.save(ready);
        Title draft = title(TitleType.MOVIE, "Draft " + tag, null, MaturityRating.PG, false);

        as(adult, get("/api/titles/" + movie.getId()))
                .andExpect(jsonPath("$.videoAssetId").value(ready.getId()))
                .andExpect(jsonPath("$.durationSeconds").value(90));
        as(adult, get("/api/titles/" + draft.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private ResultActions as(Profile profile, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer).header("X-Profile-Id", profile.getId()));
    }

    private Title title(TitleType type, String name, String synopsis, MaturityRating rating, boolean published, Genre... genres) {
        Title title = new Title(type, name, synopsis, 2024, rating);
        title.setPublished(published);
        title.getGenres().addAll(List.of(genres));
        return titleRepository.saveAndFlush(title);
    }
}
