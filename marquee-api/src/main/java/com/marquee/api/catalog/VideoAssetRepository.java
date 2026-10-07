package com.marquee.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoAssetRepository extends JpaRepository<VideoAsset, Long> {
    List<VideoAsset> findByTitle_IdOrderByCreatedAtDesc(Long titleId);

    Optional<VideoAsset> findFirstByTitle_IdAndStatusOrderByCreatedAtDesc(Long titleId, VideoAssetStatus status);
}
