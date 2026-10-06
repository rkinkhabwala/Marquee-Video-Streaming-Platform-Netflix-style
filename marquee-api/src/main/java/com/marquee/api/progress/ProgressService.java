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
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProgressService {
    private final ProfileRepository profileRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final MyListRepository myListRepository;
    private final TitleRepository titleRepository;
    private final GenreRepository genreRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;

    public ProgressService(ProfileRepository profileRepository,
                           VideoAssetRepository videoAssetRepository,
                           WatchProgressRepository watchProgressRepository,
                           MyListRepository myListRepository,
                           TitleRepository titleRepository,
                           GenreRepository genreRepository,
                           SeasonRepository seasonRepository,
                           EpisodeRepository episodeRepository) {
        this.profileRepository = profileRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.myListRepository = myListRepository;
        this.titleRepository = titleRepository;
        this.genreRepository = genreRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
    }

    @Transactional
    public WatchProgressResponse saveProgress(Long userId, Long profileId, Long assetId, Integer positionSeconds) {
        if (positionSeconds == null || positionSeconds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "positionSeconds must be >= 0");
        }

        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        WatchProgress progress = watchProgressRepository.findByProfile_IdAndVideoAsset_Id(profileId, assetId)
                .orElseGet(() -> new WatchProgress(profile, asset));

        int duration = asset.getDurationSeconds() == null ? 0 : asset.getDurationSeconds();
        progress.setPositionSeconds(positionSeconds);
        progress.setDurationSeconds(duration);
        progress.setCompleted(duration > 0 && positionSeconds >= duration);
        progress.setUpdatedAt(Instant.now());
        progress.setProfile(profile);
        progress.setVideoAsset(asset);

        WatchProgress saved = watchProgressRepository.save(progress);
        return new WatchProgressResponse(assetId, saved.getPositionSeconds(), saved.getDurationSeconds(), saved.isCompleted());
    }

    @Transactional
    public void addToMyList(Long userId, Long profileId, Long titleId) {
        profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));

        if (myListRepository.existsByProfile_IdAndTitle_Id(profileId, titleId)) {
            return;
        }

        Profile profile = profileRepository.getReferenceById(profileId);
        myListRepository.save(new MyList(profile, title));
    }

    @Transactional
    public void removeFromMyList(Long userId, Long profileId, Long titleId) {
        profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        myListRepository.deleteByProfile_IdAndTitle_Id(profileId, titleId);
    }

    @Transactional
    public List<HomeRowResponse> getHome(Long userId, Long profileId) {
        profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        List<HomeRowResponse> rows = new ArrayList<>();
        rows.add(new HomeRowResponse("Continue Watching", buildContinueWatching(profileId)));
        rows.add(new HomeRowResponse("My List", buildMyList(profileId)));
        rows.add(new HomeRowResponse("Trending", buildTrending(profileId)));
        rows.add(new HomeRowResponse("New Releases", buildNewReleases(profileId)));
        rows.addAll(buildGenreRows(profileId));
        return rows;
    }

    @Transactional
    public NextEpisodeResponse getNextEpisode(Long userId, Long profileId, Long titleId) {
        profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        if (title.getType() != TitleType.SERIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is not a series");
        }

        List<Episode> episodes = new ArrayList<>();
        seasonRepository.findByTitleIdOrderBySeasonNumberAsc(titleId)
                .forEach(season -> episodes.addAll(episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId())));
        episodes.sort(Comparator.comparing((Episode e) -> e.getSeason() == null ? 0 : e.getSeason().getSeasonNumber())
                .thenComparing(Episode::getEpisodeNumber));

        if (episodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No episodes found for this series");
        }

        Optional<WatchProgress> latest = watchProgressRepository.findByProfile_IdOrderByUpdatedAtDesc(profileId, PageRequest.of(0, 25)).stream()
                .filter(progress -> progress.getVideoAsset() != null
                        && progress.getVideoAsset().getTitle() != null
                        && progress.getVideoAsset().getTitle().getId().equals(titleId))
                .findFirst();

        if (latest.isEmpty()) {
            return toNextEpisodeResponse(episodes.get(0), titleId);
        }

        Long currentAssetId = latest.get().getVideoAsset().getId();
        for (int i = 0; i < episodes.size(); i++) {
            Episode current = episodes.get(i);
            if (current.getVideoAsset() != null && currentAssetId.equals(current.getVideoAsset().getId())) {
                if (i + 1 < episodes.size()) {
                    return toNextEpisodeResponse(episodes.get(i + 1), titleId);
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No next episode available");
            }
        }

        return toNextEpisodeResponse(episodes.get(0), titleId);
    }

    private List<TitleCardResponse> buildContinueWatching(Long profileId) {
        List<WatchProgress> watchProgress = Optional.ofNullable(
                watchProgressRepository.findByProfile_IdAndCompletedFalseOrderByUpdatedAtDesc(profileId, PageRequest.of(0, 10)))
                .orElse(List.of());
        return watchProgress.stream()
                .map(progress -> toTitleCard(progress, profileId))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<TitleCardResponse> buildMyList(Long profileId) {
        List<MyList> myList = Optional.ofNullable(myListRepository.findByProfile_IdOrderByAddedAtDesc(profileId))
                .orElse(List.of());
        return myList.stream()
                .map(item -> toTitleCard(item.getTitle(), profileId, null))
                .toList();
    }

    private List<TitleCardResponse> buildTrending(Long profileId) {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<WatchProgress> recent = Optional.ofNullable(
                watchProgressRepository.findByUpdatedAtAfterOrderByUpdatedAtDesc(since, PageRequest.of(0, 50)))
                .orElse(List.of());
        Map<Long, Long> counts = new HashMap<>();
        for (WatchProgress progress : recent) {
            if (progress.getVideoAsset() == null || progress.getVideoAsset().getTitle() == null) {
                continue;
            }
            counts.merge(progress.getVideoAsset().getTitle().getId(), 1L, Long::sum);
        }
        List<Long> titleIds = counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        return titleIds.stream()
                .map(titleRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(title -> toTitleCard(title, profileId, null))
                .toList();
    }

    private List<TitleCardResponse> buildNewReleases(Long profileId) {
        List<Title> recentTitles = Optional.ofNullable(titleRepository.findTop10ByPublishedTrueOrderByCreatedAtDesc(PageRequest.of(0, 10)))
                .orElse(List.of());
        return recentTitles.stream()
                .map(title -> toTitleCard(title, profileId, null))
                .toList();
    }

    private List<HomeRowResponse> buildGenreRows(Long profileId) {
        List<HomeRowResponse> rows = new ArrayList<>();
        List<Genre> genres = Optional.ofNullable(genreRepository.findAllByOrderByNameAsc()).orElse(List.of());
        for (Genre genre : genres) {
            List<Title> titles = Optional.ofNullable(titleRepository.findByGenres_IdAndPublishedTrueOrderByCreatedAtDesc(genre.getId(), PageRequest.of(0, 6)))
                    .orElse(List.of());
            if (!titles.isEmpty()) {
                rows.add(new HomeRowResponse(genre.getName(), titles.stream()
                        .map(title -> toTitleCard(title, profileId, null))
                        .toList()));
            }
        }
        return rows;
    }

    private TitleCardResponse toTitleCard(WatchProgress progress, Long profileId) {
        if (progress.getVideoAsset() == null || progress.getVideoAsset().getTitle() == null) {
            return null;
        }
        return toTitleCard(progress.getVideoAsset().getTitle(), profileId, progress.getPositionSeconds());
    }

    private TitleCardResponse toTitleCard(Title title, Long profileId, Integer resumeAt) {
        return new TitleCardResponse(
                title.getId(),
                title.getName(),
                title.getSynopsis(),
                title.getType() == null ? null : title.getType().name(),
                title.getMaturityRating() == null ? null : title.getMaturityRating().dbValue(),
                title.getPosterKey(),
                title.getBackdropKey(),
                resumeAt,
                profileId != null && title.getId() != null && myListRepository.existsByProfile_IdAndTitle_Id(profileId, title.getId()));
    }

    private NextEpisodeResponse toNextEpisodeResponse(Episode episode, Long titleId) {
        Season season = episode.getSeason();
        return new NextEpisodeResponse(
                episode.getId(),
                season != null ? season.getId() : null,
                season != null ? season.getSeasonNumber() : null,
                episode.getEpisodeNumber(),
                episode.getName(),
                episode.getVideoAsset() != null ? episode.getVideoAsset().getId() : null,
                titleId);
    }
}
