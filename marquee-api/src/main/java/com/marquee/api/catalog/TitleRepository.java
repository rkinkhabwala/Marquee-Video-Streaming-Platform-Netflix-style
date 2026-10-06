package com.marquee.api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TitleRepository extends JpaRepository<Title, Long> {
    /**
     * JPQL predicate for titles a profile may see, on alias {@code t}. Requires the
     * {@code :kids} and {@code :kidsSafe} parameters (pass {@link MaturityRating#KIDS_SAFE}).
     */
    String VISIBLE_TO_PROFILE = "t.published = true and (:kids = false or t.maturityRating in :kidsSafe)";

    @EntityGraph(attributePaths = {"genres"})
    Optional<Title> findWithGenresById(Long id);

    @Query("select t from Title t where " + VISIBLE_TO_PROFILE + " order by t.createdAt desc")
    List<Title> findNewReleases(@Param("kids") boolean kids,
                                @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                                Pageable pageable);

    @Query("select t from Title t join t.genres g where g.id = :genreId and " + VISIBLE_TO_PROFILE
            + " order by t.createdAt desc")
    List<Title> findVisibleByGenre(@Param("genreId") Long genreId,
                                   @Param("kids") boolean kids,
                                   @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                                   Pageable pageable);

    @Query("select g from Title t join t.genres g where " + VISIBLE_TO_PROFILE
            + " group by g order by count(t) desc, g.name asc")
    List<Genre> findTopGenres(@Param("kids") boolean kids,
                              @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                              Pageable pageable);
}
