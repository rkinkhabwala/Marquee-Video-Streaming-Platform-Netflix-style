package com.marquee.api.recsys;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {
    /** Oldest entries first; SKIP LOCKED lets several API instances relay without double-sending. */
    @Query(value = "SELECT * FROM recsys_outbox ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEntry> lockBatch(@Param("limit") int limit);

    List<OutboxEntry> findByKindOrderByIdAsc(OutboxEntry.Kind kind);

    @Modifying
    @Query("delete from OutboxEntry e where e.kind = :kind and e.createdAt < :before")
    int deleteOlderThan(@Param("kind") OutboxEntry.Kind kind, @Param("before") Instant before);
}
