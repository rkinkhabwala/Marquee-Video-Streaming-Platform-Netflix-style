package com.marquee.api.catalog;

import static com.marquee.api.catalog.MaturityRating.KIDS_SAFE;

import com.marquee.api.profile.Profile;
import com.marquee.api.progress.MyListRepository;
import com.marquee.api.progress.Rating;
import com.marquee.api.progress.RatingId;
import com.marquee.api.progress.RatingRepository;
import com.marquee.api.progress.WatchProgress;
import com.marquee.api.progress.WatchProgressRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Viewer-facing catalog: title detail, browse and search, all scoped to the active profile. */
@Service
public class CatalogService {
    static final int MAX_PAGE_SIZE = 50;
    private static final List<String> KIDS_SAFE_DB_VALUES = KIDS_SAFE.stream().map(MaturityRating::dbValue).sorted().toList();

    private final TitleAccess titleAccess;
    private final TitleRepository titleRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final MyListRepository myListRepository;
    private final RatingRepository ratingRepository;
    private final WatchProgressRepository watchProgressRepository;

    public CatalogService(TitleAccess titleAccess,
                          TitleRepository titleRepository,
                          SeasonRepository seasonRepository,
                          EpisodeRepository episodeRepository,
                          VideoAssetRepository videoAssetRepository,
                          MyListRepository myListRepository,
                          RatingRepository ratingRepository,
                          WatchProgressRepository watchProgressRepository) {
        this.titleAccess = titleAccess;
        this.titleRepository = titleRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.myListRepository = myListRepository;
        this.ratingRepository = ratingRepository;
        this.watchProgressRepository = watchProgressRepository;
    }

    @Transactional(readOnly = true)
    public TitleDetailResponse getTitle(Long userId, Long profileId, Long titleId) {
        Profile profile = titleAccess.requireProfile(userId, profileId);
        Title title = titleAccess.requireVisibleTitle(profile, titleId);

        Integer rating = ratingRepository.findById(new RatingId(profileId, titleId)).map(Rating::getThumbs).orElse(null);
        boolean inMyList = myListRepository.existsByProfile_IdAndTitle_Id(profileId, titleId);
        List<String> genres = title.getGenres().stream().map(Genre::getName).sorted().toList();

        Long videoAssetId = null;
        Integer durationSeconds = null;
        Integer resumeAt = null;
        List<TitleDetailResponse.SeasonDetail> seasons = List.of();

        if (title.getType() == TitleType.MOVIE) {
            VideoAsset asset = videoAssetRepository.findFirstByTitle_IdAndStatusOrderByCreatedAtDesc(titleId, VideoAssetStatus.READY)
                    .orElse(null);
            if (asset != null) {
                videoAssetId = asset.getId();
                durationSeconds = asset.getDurationSeconds();
                resumeAt = resumePositions(profileId, List.of(asset.getId())).get(asset.getId());
            }
        } else {
            seasons = buildSeasons(profileId, titleId);
        }

        return new TitleDetailResponse(title.getId(), title.getType().name(), title.getName(), title.getSynopsis(),
                title.getReleaseYear(), title.getMaturityRating() == null ? null : title.getMaturityRating().dbValue(),
                title.getPosterKey(), title.getBackdropKey(), genres, inMyList, rating,
                videoAssetId, durationSeconds, resumeAt, seasons);
    }

    @Transactional(readOnly = true)
    public PageResponse<TitleCardResponse> browse(Long userId, Long profileId, String genre, TitleType type, int page, int size) {
        Profile profile = titleAccess.requireProfile(userId, profileId);
        String lowerCaseGenre = genre == null || genre.isBlank() ? null : genre.trim().toLowerCase(Locale.ROOT);
        Set<Long> myListIds = myListRepository.findTitleIdsByProfileId(profileId);
        return PageResponse.of(
                titleRepository.browse(type, lowerCaseGenre, profile.isKids(), KIDS_SAFE, pageable(page, size)),
                title -> TitleCardResponse.of(title, myListIds.contains(title.getId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<TitleCardResponse> search(Long userId, Long profileId, String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }
        Profile profile = titleAccess.requireProfile(userId, profileId);
        Set<Long> myListIds = myListRepository.findTitleIdsByProfileId(profileId);
        return PageResponse.of(
                titleRepository.search(query.trim(), profile.isKids(), KIDS_SAFE_DB_VALUES, pageable(page, size)),
                title -> TitleCardResponse.of(title, myListIds.contains(title.getId())));
    }

    private List<TitleDetailResponse.SeasonDetail> buildSeasons(Long profileId, Long titleId) {
        List<Episode> episodes = episodeRepository.findByTitleIdInWatchOrder(titleId);
        Map<Long, Integer> resume = resumePositions(profileId, episodes.stream()
                .map(Episode::getVideoAsset).filter(Objects::nonNull).map(VideoAsset::getId).toList());
        Map<Long, List<Episode>> bySeason = episodes.stream()
                .collect(Collectors.groupingBy(e -> e.getSeason().getId(), LinkedHashMap::new, Collectors.toList()));

        return seasonRepository.findByTitleIdOrderBySeasonNumberAsc(titleId).stream()
                .map(season -> new TitleDetailResponse.SeasonDetail(season.getId(), season.getSeasonNumber(), season.getName(),
                        bySeason.getOrDefault(season.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Episode::getEpisodeNumber))
                                .map(episode -> toEpisodeDetail(episode, resume))
                                .toList()))
                .toList();
    }

    private TitleDetailResponse.EpisodeDetail toEpisodeDetail(Episode episode, Map<Long, Integer> resume) {
        VideoAsset asset = episode.getVideoAsset();
        return new TitleDetailResponse.EpisodeDetail(episode.getId(), episode.getEpisodeNumber(), episode.getName(),
                episode.getSynopsis(),
                asset == null ? null : asset.getId(),
                asset == null ? null : asset.getDurationSeconds(),
                asset != null && asset.getStatus() == VideoAssetStatus.READY,
                asset == null ? null : resume.get(asset.getId()));
    }

    /** Resume position per asset, only for assets started but not completed. */
    private Map<Long, Integer> resumePositions(Long profileId, List<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return watchProgressRepository.findByProfile_IdAndVideoAsset_IdIn(profileId, assetIds).stream()
                .filter(progress -> !progress.isCompleted() && progress.getPositionSeconds() > 0)
                .collect(Collectors.toMap(progress -> progress.getVideoAsset().getId(), WatchProgress::getPositionSeconds));
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size);
    }
}
