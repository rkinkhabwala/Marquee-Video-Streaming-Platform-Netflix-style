package com.marquee.api.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(Long seasonId);

    @Query("select e from Episode e join fetch e.season s left join fetch e.videoAsset "
            + "where s.title.id = :titleId order by s.seasonNumber asc, e.episodeNumber asc")
    List<Episode> findByTitleIdInWatchOrder(@Param("titleId") Long titleId);
}
