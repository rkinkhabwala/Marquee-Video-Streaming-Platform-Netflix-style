package com.marquee.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {
    @EntityGraph(attributePaths = {"genres"})
    Optional<Title> findWithGenresById(Long id);

    List<Title> findTop10ByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);

    List<Title> findByGenres_IdAndPublishedTrueOrderByCreatedAtDesc(Long genreId, Pageable pageable);
}
