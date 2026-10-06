package com.marquee.api.progress;

import static com.marquee.api.catalog.TitleRepository.VISIBLE_TO_PROFILE;

import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchProgressRepository extends JpaRepository<WatchProgress, WatchProgressId> {
    Optional<WatchProgress> findByProfile_IdAndVideoAsset_Id(Long profileId, Long videoAssetId);

    Optional<WatchProgress> findFirstByProfile_IdAndVideoAsset_IdInOrderByUpdatedAtDesc(Long profileId,
                                                                                         Collection<Long> videoAssetIds);

    @Query("select wp from WatchProgress wp join fetch wp.videoAsset va join fetch va.title t "
            + "where wp.profile.id = :profileId and " + VISIBLE_TO_PROFILE + " order by wp.updatedAt desc")
    List<WatchProgress> findRecentVisibleForProfile(@Param("profileId") Long profileId,
                                                    @Param("kids") boolean kids,
                                                    @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                                                    Pageable pageable);

    @Query("select t from WatchProgress wp join wp.videoAsset va join va.title t "
            + "where wp.updatedAt >= :since and " + VISIBLE_TO_PROFILE
            + " group by t order by count(distinct wp.profile.id) desc, max(wp.updatedAt) desc")
    List<Title> findTrendingTitles(@Param("since") Instant since,
                                   @Param("kids") boolean kids,
                                   @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                                   Pageable pageable);
}
