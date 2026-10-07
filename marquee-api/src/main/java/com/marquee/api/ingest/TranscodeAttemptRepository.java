package com.marquee.api.ingest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscodeAttemptRepository extends JpaRepository<TranscodeAttempt, Long> {
    Optional<TranscodeAttempt> findFirstByVideoAsset_IdAndAttemptOrderByIdDesc(Long videoAssetId, int attempt);

    List<TranscodeAttempt> findByVideoAsset_IdOrderByIdAsc(Long videoAssetId);
}
