package com.marquee.api.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(Long seasonId);
}
