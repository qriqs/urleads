package com.urleads.comercial.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.entity.OutboxStatus;
import com.urleads.comercial.event.EventEnvelope;
import com.urleads.comercial.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
        OutboxEventRepository outboxEventRepository,
        RabbitTemplate rabbitTemplate,
        ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    /**
     * Persiste el evento en la tabla outbox dentro de la misma transacción SQL.
     */
    public <T> OutboxEvent createAndSaveEvent(String eventType, int version, String ownerId, T data) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<T> envelope = new EventEnvelope<>(
            eventId,
            eventType,
            version,
            Instant.now(),
            ownerId,
            data
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al serializar el evento outbox a JSON", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(eventId, eventType, version, ownerId, payloadJson);
        return outboxEventRepository.save(outboxEvent);
    }

    /**
     * Despacha el evento hacia el broker RabbitMQ y actualiza su estado.
     */
    public void publishEvent(OutboxEvent event) {
        if (event == null || event.getStatus() == OutboxStatus.PROCESSED) {
            return;
        }

        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EVENTS_EXCHANGE, event.getEventType(), event.getPayload());
            event.markProcessed();
            outboxEventRepository.save(event);
            log.info("Evento outbox despachado exitosamente hacia RabbitMQ: {} ({})", event.getEventType(), event.getId());
        } catch (Exception ex) {
            log.warn("No se pudo conectar con RabbitMQ para despachar evento {}: {}. Permanecerá en outbox como PENDING.",
                event.getId(), ex.getMessage());
        }
    }

    public void publishEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        publishEvent(event);
    }
}
