package com.marquee.api.progress;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyListRepository extends JpaRepository<MyList, MyListId> {
    List<MyList> findByProfile_IdOrderByAddedAtDesc(Long profileId);

    Optional<MyList> findByProfile_IdAndTitle_Id(Long profileId, Long titleId);

    boolean existsByProfile_IdAndTitle_Id(Long profileId, Long titleId);

    void deleteByProfile_IdAndTitle_Id(Long profileId, Long titleId);
}
