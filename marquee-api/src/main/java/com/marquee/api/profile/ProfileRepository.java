package com.marquee.api.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    List<Profile> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Profile> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
