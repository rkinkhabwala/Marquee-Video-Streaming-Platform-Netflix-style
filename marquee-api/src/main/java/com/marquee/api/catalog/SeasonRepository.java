package com.marquee.api.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Long> {
    List<Season> findByTitleIdOrderBySeasonNumberAsc(Long titleId);
    List<Season> findByTitleId(Long titleId);
}
