package com.urleads.repository;

import com.urleads.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    Optional<ProcessedEvent> findByEventId(String eventId);
}
