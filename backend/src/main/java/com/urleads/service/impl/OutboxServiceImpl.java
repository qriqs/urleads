package com.urleads.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.entity.Lead;
import com.urleads.entity.OutboxEvent;
import com.urleads.entity.OutboxStatus;
import com.urleads.event.CrmEventTypes;
import com.urleads.event.EventEnvelope;
import com.urleads.event.LeadDeletedEventData;
import com.urleads.event.LeadEventData;
import com.urleads.repository.OutboxRepository;
import com.urleads.service.OutboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implementación de {@link OutboxService} encargada de persistir eventos de dominio
 * atómicamente con los cambios de leads en la tabla outbox (Backend V2 C04).
 */
@Service
@Transactional
public class OutboxServiceImpl implements OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OutboxServiceImpl(@Autowired(required = false) OutboxRepository outboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public OutboxServiceImpl(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this(outboxRepository, objectMapper, Clock.systemUTC());
    }

    @Override
    public OutboxEvent recordLeadCreated(Long usuarioId, Lead lead) {
        LeadEventData data = new LeadEventData(
                lead.getId(),
                lead.getEtapa(),
                lead.getProximoSeguimiento()
        );
        return persistEvent(usuarioId, CrmEventTypes.LEAD_CREATED, data);
    }

    @Override
    public OutboxEvent recordLeadUpdated(Long usuarioId, Lead lead) {
        LeadEventData data = new LeadEventData(
                lead.getId(),
                lead.getEtapa(),
                lead.getProximoSeguimiento()
        );
        return persistEvent(usuarioId, CrmEventTypes.LEAD_UPDATED, data);
    }

    @Override
    public OutboxEvent recordLeadDeleted(Long usuarioId, Long leadId) {
        LeadDeletedEventData data = new LeadDeletedEventData(leadId);
        return persistEvent(usuarioId, CrmEventTypes.LEAD_DELETED, data);
    }

    private <T> OutboxEvent persistEvent(Long usuarioId, String eventType, T data) {
        if (outboxRepository == null) {
            throw new IllegalStateException("OutboxRepository no configurado");
        }
        String eventId = UUID.randomUUID().toString();
        String occurredAt = Instant.now(clock).toString();
        String ownerId = String.valueOf(usuarioId);

        EventEnvelope<T> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                1,
                occurredAt,
                ownerId,
                data
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al serializar el evento outbox " + eventType, e);
        }

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setRoutingKey(eventType);
        event.setExchange(CrmEventTypes.EXCHANGE);
        event.setPayload(payload);
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)));

        return outboxRepository.save(event);
    }
}
