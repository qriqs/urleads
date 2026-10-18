package com.urleads.comercial.repository;

import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxStatus status);
    List<OutboxEvent> findByOwnerId(String ownerId);
}
