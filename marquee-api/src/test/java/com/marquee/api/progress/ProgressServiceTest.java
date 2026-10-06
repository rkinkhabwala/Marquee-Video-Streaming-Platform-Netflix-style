package com.marquee.api.progress;

import com.marquee.api.catalog.Episode;
import com.marquee.api.catalog.EpisodeRepository;
import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.GenreRepository;
import com.marquee.api.catalog.Season;
import com.marquee.api.catalog.SeasonRepository;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.user.User;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private WatchProgressRepository watchProgressRepository;

    @Mock
    private MyListRepository myListRepository;

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @InjectMocks
    private ProgressService progressService;

    @Test
    void saveProgressPersistsCurrentPosition() throws Exception {
        User user = new User("u@example.com", "hash");
        setId(user, 1L);
        Profile profile = new Profile(user, "Kid", null, true);
        setId(profile, 5L);
        Title title = new Title(TitleType.MOVIE, "Sample", "synopsis", 2024, null);
        setId(title, 22L);
        VideoAsset asset = new VideoAsset(title, com.marquee.api.catalog.VideoAssetStatus.READY);
        setId(asset, 99L);
        asset.setDurationSeconds(600);

        when(profileRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(profile));
        when(videoAssetRepository.findById(99L)).thenReturn(Optional.of(asset));
        when(watchProgressRepository.findByProfile_IdAndVideoAsset_Id(5L, 99L)).thenReturn(Optional.empty());
        when(watchProgressRepository.save(org.mockito.ArgumentMatchers.any(WatchProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WatchProgressResponse response = progressService.saveProgress(1L, 5L, 99L, 180);

        assertEquals(99L, response.assetId());
        assertEquals(180, response.positionSeconds());
        assertEquals(600, response.durationSeconds());
    }

    @Test
    void getHomeReturnsContinueWatchingAndMyListRows() throws Exception {
        User user = new User("u@example.com", "hash");
        setId(user, 2L);
        Profile profile = new Profile(user, "Parent", null, false);
        setId(profile, 6L);
        Title title = new Title(TitleType.MOVIE, "Arrival", "sci-fi", 2016, null);
        setId(title, 33L);
        VideoAsset asset = new VideoAsset(title, com.marquee.api.catalog.VideoAssetStatus.READY);
        setId(asset, 111L);
        asset.setDurationSeconds(500);
        WatchProgress progress = new WatchProgress(profile, asset);
        progress.setPositionSeconds(200);
        progress.setDurationSeconds(500);
        progress.setUpdatedAt(Instant.now());
        progress.setCompleted(false);

        when(profileRepository.findByIdAndUserId(6L, 2L)).thenReturn(Optional.of(profile));
        when(watchProgressRepository.findByProfile_IdAndCompletedFalseOrderByUpdatedAtDesc(6L, PageRequest.of(0, 10))).thenReturn(List.of(progress));
        when(myListRepository.findByProfile_IdOrderByAddedAtDesc(6L)).thenReturn(List.of());
        when(genreRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(titleRepository.findTop10ByPublishedTrueOrderByCreatedAtDesc(PageRequest.of(0, 10))).thenReturn(List.of());

        List<HomeRowResponse> rows = progressService.getHome(2L, 6L);

        assertEquals(4, rows.size());
    }

    @Test
    void getNextEpisodeReturnsFollowingEpisode() throws Exception {
        User user = new User("u@example.com", "hash");
        setId(user, 3L);
        Profile profile = new Profile(user, "Viewer", null, false);
        setId(profile, 7L);
        Title title = new Title(TitleType.SERIES, "Show", "desc", 2024, null);
        setId(title, 44L);

        Season season = new Season(title, 1, "Season 1");
        setId(season, 80L);
        Episode first = new Episode(season, 1, "First", "desc", null);
        setId(first, 10L);
        Episode second = new Episode(season, 2, "Second", "desc", null);
        setId(second, 11L);
        VideoAsset firstAsset = new VideoAsset(title, com.marquee.api.catalog.VideoAssetStatus.READY);
        setId(firstAsset, 12L);
        first.setVideoAsset(firstAsset);
        VideoAsset secondAsset = new VideoAsset(title, com.marquee.api.catalog.VideoAssetStatus.READY);
        setId(secondAsset, 13L);
        second.setVideoAsset(secondAsset);

        when(profileRepository.findByIdAndUserId(7L, 3L)).thenReturn(Optional.of(profile));
        when(titleRepository.findById(44L)).thenReturn(Optional.of(title));
        when(seasonRepository.findByTitleIdOrderBySeasonNumberAsc(44L)).thenReturn(List.of(season));
        when(episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(80L)).thenReturn(List.of(first, second));

        WatchProgress progress = new WatchProgress(profile, firstAsset);
        progress.setPositionSeconds(100);
        progress.setUpdatedAt(Instant.now());
        when(watchProgressRepository.findByProfile_IdOrderByUpdatedAtDesc(7L, PageRequest.of(0, 25))).thenReturn(List.of(progress));

        NextEpisodeResponse next = progressService.getNextEpisode(3L, 7L, 44L);

        assertEquals(11L, next.episodeId());
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
