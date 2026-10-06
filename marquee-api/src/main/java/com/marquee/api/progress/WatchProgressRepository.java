package com.marquee.api.progress;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchProgressRepository extends JpaRepository<WatchProgress, WatchProgressId> {
    Optional<WatchProgress> findByProfile_IdAndVideoAsset_Id(Long profileId, Long videoAssetId);

    List<WatchProgress> findByProfile_IdAndCompletedFalseOrderByUpdatedAtDesc(Long profileId, Pageable pageable);

    List<WatchProgress> findByUpdatedAtAfterOrderByUpdatedAtDesc(Instant since, Pageable pageable);

    List<WatchProgress> findByProfile_IdOrderByUpdatedAtDesc(Long profileId, Pageable pageable);
}
