package com.marquee.api.progress;

import static com.marquee.api.catalog.MaturityRating.KIDS_SAFE;

import com.marquee.api.catalog.Episode;
import com.marquee.api.catalog.EpisodeRepository;
import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.Season;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleAccess;
import com.marquee.api.catalog.TitleCardResponse;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.profile.Profile;
import com.marquee.api.recsys.EngagementRecorder;
import com.marquee.api.recsys.EngagementType;
import com.marquee.api.recsys.RecommendedRows;
import com.marquee.api.recsys.WatchMilestones;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProgressService {
    static final double COMPLETION_THRESHOLD = 0.95;
    private static final int ROW_SIZE = 10;
    private static final int GENRE_ROW_COUNT = 5;
    private static final int TRENDING_WINDOW_DAYS = 7;
    // Progress is stored per asset, so a series contributes one row per episode watched.
    private static final int CONTINUE_WATCHING_SCAN = 100;

    private final TitleAccess titleAccess;
    private final VideoAssetRepository videoAssetRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final MyListRepository myListRepository;
    private final TitleRepository titleRepository;
    private final EpisodeRepository episodeRepository;
    private final RatingRepository ratingRepository;
    private final EngagementRecorder engagement;
    private final RecommendedRows recommendedRows;

    public ProgressService(TitleAccess titleAccess,
                           VideoAssetRepository videoAssetRepository,
                           WatchProgressRepository watchProgressRepository,
                           MyListRepository myListRepository,
                           TitleRepository titleRepository,
                           EpisodeRepository episodeRepository,
                           RatingRepository ratingRepository,
                           EngagementRecorder engagement,
                           RecommendedRows recommendedRows) {
        this.titleAccess = titleAccess;
        this.videoAssetRepository = videoAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.myListRepository = myListRepository;
        this.titleRepository = titleRepository;
        this.episodeRepository = episodeRepository;
        this.ratingRepository = ratingRepository;
        this.engagement = engagement;
        this.recommendedRows = recommendedRows;
    }

    @Transactional
    public WatchProgressResponse saveProgress(Long userId, Long profileId, Long assetId, Integer positionSeconds) {
        if (positionSeconds == null || positionSeconds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "positionSeconds must be >= 0");
        }

        Profile profile = requireProfile(userId, profileId);
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        WatchProgress progress = watchProgressRepository.findByProfile_IdAndVideoAsset_Id(profileId, assetId)
                .orElseGet(() -> new WatchProgress(profile, asset));

        int duration = asset.getDurationSeconds() == null ? 0 : asset.getDurationSeconds();
        progress.setPositionSeconds(positionSeconds);
        progress.setDurationSeconds(duration);
        progress.setCompleted(duration > 0 && positionSeconds >= duration * COMPLETION_THRESHOLD);
        progress.setUpdatedAt(Instant.now());

        WatchMilestones.Result milestones = WatchMilestones.advance(progress.getMilestone(), positionSeconds, duration, progress.isCompleted());
        progress.setMilestone(milestones.milestone());
        if (asset.getTitle() != null) {
            for (EngagementType reached : milestones.reached()) {
                engagement.record(reached, profileId, asset.getTitle().getId(), positionSeconds, duration);
            }
        }

        WatchProgress saved = watchProgressRepository.save(progress);
        return new WatchProgressResponse(assetId, saved.getPositionSeconds(), saved.getDurationSeconds(), saved.isCompleted());
    }

    @Transactional
    public void addToMyList(Long userId, Long profileId, Long titleId) {
        Profile profile = requireProfile(userId, profileId);
        Title title = requireVisibleTitle(profile, titleId);

        if (myListRepository.existsByProfile_IdAndTitle_Id(profileId, titleId)) {
            return;
        }
        myListRepository.save(new MyList(profile, title));
        engagement.record(EngagementType.MY_LIST_ADD, profileId, titleId, null, null);
    }

    @Transactional
    public void removeFromMyList(Long userId, Long profileId, Long titleId) {
        requireProfile(userId, profileId);
        myListRepository.deleteByProfile_IdAndTitle_Id(profileId, titleId);
    }

    @Transactional
    public void rate(Long userId, Long profileId, Long titleId, Integer value) {
        if (value == null || (value != 1 && value != -1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must be 1 or -1");
        }
        Profile profile = requireProfile(userId, profileId);
        requireVisibleTitle(profile, titleId);
        Rating rating = ratingRepository.findById(new RatingId(profileId, titleId)).orElse(null);
        if (rating != null && rating.getThumbs() == value) {
            return; // unchanged: no new signal for recsys
        }
        if (rating == null) {
            ratingRepository.save(new Rating(profileId, titleId, value));
        } else {
            rating.setThumbs(value);
        }
        engagement.record(value == 1 ? EngagementType.THUMBS_UP : EngagementType.THUMBS_DOWN, profileId, titleId, null, null);
    }

    @Transactional(readOnly = true)
    public List<HomeRowResponse> getHome(Long userId, Long profileId) {
        Profile profile = requireProfile(userId, profileId);
        boolean kids = profile.isKids();
        Set<Long> myListIds = myListRepository.findTitleIdsByProfileId(profileId);

        List<WatchProgress> recent = watchProgressRepository.findRecentVisibleForProfile(
                profileId, kids, KIDS_SAFE, PageRequest.of(0, CONTINUE_WATCHING_SCAN));
        Title lastWatched = recent.isEmpty() ? null : recent.get(0).getVideoAsset().getTitle();
        List<Title> trending = watchProgressRepository.findTrendingTitles(
                Instant.now().minus(TRENDING_WINDOW_DAYS, ChronoUnit.DAYS), kids, KIDS_SAFE, PageRequest.of(0, ROW_SIZE));

        List<HomeRowResponse> rows = new ArrayList<>();
        rows.add(new HomeRowResponse("Continue Watching", buildContinueWatching(recent, myListIds)));
        for (RecommendedRows.Row row : recommendedRows.build(profile, lastWatched, trending)) {
            rows.add(new HomeRowResponse(row.title(), toTitleCards(row.titles(), myListIds)));
        }
        rows.add(new HomeRowResponse("My List", myListRepository.findVisibleForProfile(profileId, kids, KIDS_SAFE).stream()
                .map(item -> toTitleCard(item.getTitle(), myListIds, null))
                .toList()));
        rows.add(new HomeRowResponse("Trending", toTitleCards(trending, myListIds)));
        rows.add(new HomeRowResponse("New Releases", toTitleCards(
                titleRepository.findNewReleases(kids, KIDS_SAFE, PageRequest.of(0, ROW_SIZE)), myListIds)));
        for (Genre genre : titleRepository.findTopGenres(kids, KIDS_SAFE, PageRequest.of(0, GENRE_ROW_COUNT))) {
            rows.add(new HomeRowResponse(genre.getName(), toTitleCards(
                    titleRepository.findVisibleByGenre(genre.getId(), kids, KIDS_SAFE, PageRequest.of(0, ROW_SIZE)), myListIds)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public NextEpisodeResponse getNextEpisode(Long userId, Long profileId, Long titleId) {
        Profile profile = requireProfile(userId, profileId);
        Title title = requireVisibleTitle(profile, titleId);
        if (title.getType() != TitleType.SERIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is not a series");
        }

        List<Episode> episodes = episodeRepository.findByTitleIdInWatchOrder(titleId);
        if (episodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No episodes found for this series");
        }

        List<Long> assetIds = episodes.stream()
                .map(Episode::getVideoAsset)
                .filter(Objects::nonNull)
                .map(VideoAsset::getId)
                .toList();
        Optional<WatchProgress> latest = assetIds.isEmpty()
                ? Optional.empty()
                : watchProgressRepository.findFirstByProfile_IdAndVideoAsset_IdInOrderByUpdatedAtDesc(profileId, assetIds);
        if (latest.isEmpty()) {
            return toNextEpisodeResponse(episodes.get(0), titleId);
        }

        Long lastWatchedAssetId = latest.get().getVideoAsset().getId();
        for (int i = 0; i < episodes.size(); i++) {
            VideoAsset asset = episodes.get(i).getVideoAsset();
            if (asset != null && lastWatchedAssetId.equals(asset.getId())) {
                if (i + 1 < episodes.size()) {
                    return toNextEpisodeResponse(episodes.get(i + 1), titleId);
                }
                break;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No next episode available");
    }

    private List<TitleCardResponse> buildContinueWatching(List<WatchProgress> recent, Set<Long> myListIds) {
        // Keep only the most recent progress row per title; drop titles whose latest row is finished.
        Map<Long, WatchProgress> latestByTitle = new LinkedHashMap<>();
        for (WatchProgress progress : recent) {
            latestByTitle.putIfAbsent(progress.getVideoAsset().getTitle().getId(), progress);
        }
        return latestByTitle.values().stream()
                .filter(progress -> !progress.isCompleted() && progress.getPositionSeconds() > 0)
                .limit(ROW_SIZE)
                .map(progress -> toTitleCard(progress.getVideoAsset().getTitle(), myListIds, progress))
                .toList();
    }

    private Profile requireProfile(Long userId, Long profileId) {
        return titleAccess.requireProfile(userId, profileId);
    }

    private Title requireVisibleTitle(Profile profile, Long titleId) {
        return titleAccess.requireVisibleTitle(profile, titleId);
    }

    private List<TitleCardResponse> toTitleCards(List<Title> titles, Set<Long> myListIds) {
        return titles.stream()
                .map(title -> toTitleCard(title, myListIds, null))
                .toList();
    }

    private TitleCardResponse toTitleCard(Title title, Set<Long> myListIds, WatchProgress progress) {
        return progress == null
                ? TitleCardResponse.of(title, myListIds.contains(title.getId()))
                : TitleCardResponse.of(title, myListIds.contains(title.getId()),
                        progress.getPositionSeconds(), progress.getVideoAsset().getId());
    }

    private NextEpisodeResponse toNextEpisodeResponse(Episode episode, Long titleId) {
        Season season = episode.getSeason();
        return new NextEpisodeResponse(
                episode.getId(),
                season.getId(),
                season.getSeasonNumber(),
                episode.getEpisodeNumber(),
                episode.getName(),
                episode.getVideoAsset() != null ? episode.getVideoAsset().getId() : null,
                titleId);
    }
}
