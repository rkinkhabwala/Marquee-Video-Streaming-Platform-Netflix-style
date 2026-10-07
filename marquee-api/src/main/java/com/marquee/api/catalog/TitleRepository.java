package com.marquee.api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
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

    @Query("select t from Title t where " + VISIBLE_TO_PROFILE
            + " and (:type is null or t.type = :type)"
            + " and (:genre is null or exists (select 1 from Title t2 join t2.genres g where t2 = t and lower(g.name) = :genre))"
            + " order by t.createdAt desc, t.id desc")
    Page<Title> browse(@Param("type") TitleType type,
                       @Param("genre") String lowerCaseGenre,
                       @Param("kids") boolean kids,
                       @Param("kidsSafe") Collection<MaturityRating> kidsSafe,
                       Pageable pageable);

    String SEARCH_MATCH = "(t.search_vector @@ websearch_to_tsquery('english', :q) or t.name ilike ('%' || :q || '%'))"
            + " and t.published = true and (:kids = false or t.maturity_rating in (:kidsSafe))";

    /** Full-text match on name and synopsis, plus substring match on name for partial words. */
    @Query(value = "select t.* from titles t where " + SEARCH_MATCH
            + " order by ts_rank(t.search_vector, websearch_to_tsquery('english', :q)) desc, t.created_at desc, t.id desc",
            countQuery = "select count(*) from titles t where " + SEARCH_MATCH,
            nativeQuery = true)
    Page<Title> search(@Param("q") String query,
                       @Param("kids") boolean kids,
                       @Param("kidsSafe") Collection<String> kidsSafeDbValues,
                       Pageable pageable);
}
