package com.marquee.api.playback;

import com.marquee.api.progress.WatchProgressRepository;
import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.recsys.EngagementRecorder;
import com.marquee.api.user.User;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybackServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private PlaybackTokenService playbackTokenService;

    @Mock
    private WatchProgressRepository watchProgressRepository;

    @Mock
    private EngagementRecorder engagement;

    @InjectMocks
    private PlaybackService playbackService;

    @Test
    void getPlaybackReturnsSignedManifestForReadyAsset() throws Exception {
        User user = new User("u@example.com", "hash");
        setId(user, 1L);
        Profile profile = new Profile(user, "Kid", null, true);
        setId(profile, 5L);

        Title title = new Title(TitleType.MOVIE, "Toy Story", "adventure", 1995, MaturityRating.G);
        title.setPublished(true);
        setId(title, 22L);
        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.READY);
        setId(asset, 77L);
        asset.setDurationSeconds(600);

        when(profileRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(profile));
        when(videoAssetRepository.findById(77L)).thenReturn(Optional.of(asset));
        when(playbackTokenService.generate(77L, 5L)).thenReturn("signed-token");

        PlaybackResponse response = playbackService.getPlayback(1L, 5L, 77L);

        assertEquals("/stream/77/master.m3u8?token=signed-token", response.manifestUrl());
        assertEquals(600, response.durationSeconds());
    }

    @Test
    void kidsProfileCannotWatchRestrictedTitle() throws Exception {
        User user = new User("u@example.com", "hash");
        setId(user, 2L);
        Profile profile = new Profile(user, "Kid", null, true);
        setId(profile, 6L);

        Title title = new Title(TitleType.MOVIE, "Deadpool", "explicit", 2016, MaturityRating.R);
        title.setPublished(true);
        setId(title, 23L);
        VideoAsset asset = new VideoAsset(title, VideoAssetStatus.READY);
        setId(asset, 78L);

        when(profileRepository.findByIdAndUserId(6L, 2L)).thenReturn(Optional.of(profile));
        when(videoAssetRepository.findById(78L)).thenReturn(Optional.of(asset));

        assertThrows(ResponseStatusException.class, () -> playbackService.getPlayback(2L, 6L, 78L));
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
