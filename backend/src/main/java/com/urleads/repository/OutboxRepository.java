package com.urleads.repository;

import com.urleads.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now) ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingForPublishing(@Param("now") OffsetDateTime now, @Param("batchSize") int batchSize);
}
