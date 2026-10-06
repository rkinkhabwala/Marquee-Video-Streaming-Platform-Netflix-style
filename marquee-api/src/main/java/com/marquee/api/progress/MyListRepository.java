package com.marquee.api.progress;

import static com.marquee.api.catalog.TitleRepository.VISIBLE_TO_PROFILE;

import com.marquee.api.catalog.MaturityRating;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MyListRepository extends JpaRepository<MyList, MyListId> {
    @Query("select m from MyList m join fetch m.title t where m.profile.id = :profileId and "
            + VISIBLE_TO_PROFILE + " order by m.addedAt desc")
    List<MyList> findVisibleForProfile(@Param("profileId") Long profileId,
                                       @Param("kids") boolean kids,
                                       @Param("kidsSafe") Collection<MaturityRating> kidsSafe);

    @Query("select m.id.titleId from MyList m where m.profile.id = :profileId")
    Set<Long> findTitleIdsByProfileId(@Param("profileId") Long profileId);

    boolean existsByProfile_IdAndTitle_Id(Long profileId, Long titleId);

    void deleteByProfile_IdAndTitle_Id(Long profileId, Long titleId);
}
