package com.marquee.api.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {
    @EntityGraph(attributePaths = {"genres"})
    Optional<Title> findWithGenresById(Long id);
}
