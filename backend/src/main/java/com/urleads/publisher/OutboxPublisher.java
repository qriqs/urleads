package com.urleads.publisher;

import com.urleads.config.OutboxProperties;
import com.urleads.entity.OutboxEvent;
import com.urleads.entity.OutboxStatus;
import com.urleads.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publicador asíncrono para el patrón Transactional Outbox (Backend V2 C04).
 * Despacha eventos pendientes hacia el exchange canónico urleads.events.v1
 * con publisher confirms, detección de mensajes no enrutables y reintentos acotados.
 */
@Component
@ConditionalOnBean({OutboxRepository.class, RabbitTemplate.class})
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    @Autowired
    public OutboxPublisher(OutboxRepository outboxRepository,
                           RabbitTemplate rabbitTemplate,
                           OutboxProperties properties,
                           Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public OutboxPublisher(OutboxRepository outboxRepository,
                           RabbitTemplate rabbitTemplate,
                           OutboxProperties properties) {
        this(outboxRepository, rabbitTemplate, properties, Clock.systemUTC());
    }

    @Scheduled(fixedDelayString = "${urleads.outbox.publisher.interval-ms:5000}")
    @Transactional
    public void scheduledPublish() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            publishPendingEvents();
        } catch (RuntimeException e) {
            log.warn("Excepción al ejecutar el ciclo del publicador outbox: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public int publishPendingEvents() {
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
        List<OutboxEvent> pending = outboxRepository.findPendingForPublishing(now, properties.getBatchSize());
        if (pending == null || pending.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        for (OutboxEvent event : pending) {
            boolean success = publishEvent(event);
            if (success) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    public boolean publishEvent(OutboxEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(event.getEventId());

            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.getEventId())
                    .build();

            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message, correlationData);

            long timeout = properties.getConfirmTimeoutMs();
            CorrelationData.Confirm confirm = correlationData.getFuture().get(timeout, TimeUnit.MILLISECONDS);

            if (confirm == null || !confirm.isAck()) {
                String reason = (confirm != null && confirm.getReason() != null)
                        ? confirm.getReason()
                        : "Confirmación negativa (nack) recibida del broker RabbitMQ";
                recordFailure(event, reason);
                return false;
            }

            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                String reason = "Mensaje devuelto como no enrutable (código " + returned.getReplyCode()
                        + ": " + returned.getReplyText() + ")";
                recordFailure(event, reason);
                return false;
            }

            recordSuccess(event);
            return true;
        } catch (TimeoutException e) {
            recordFailure(event, "Confirmación de publicación expiró tras " + properties.getConfirmTimeoutMs() + "ms");
            return false;
        } catch (Exception e) {
            recordFailure(event, "Error en publicación: " + e.getMessage());
            return false;
        }
    }

    public void recordSuccess(OutboxEvent event) {
        event.setStatus(OutboxStatus.DELIVERED);
        event.setSentAt(OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)));
        event.setLastError(null);
        outboxRepository.save(event);
    }

    public void recordFailure(OutboxEvent event, String reason) {
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastError(reason);

        if (nextRetry >= properties.getMaxRetries()) {
            event.setStatus(OutboxStatus.FAILED);
            event.setNextRetryAt(null);
            log.error("Evento outbox {} falló definitivamente tras {} intentos: {}",
                    event.getEventId(), nextRetry, reason);
        } else {
            event.setStatus(OutboxStatus.PENDING);
            long delaySeconds = Math.min(300, (long) Math.pow(2, nextRetry) * properties.getBaseBackoffSeconds());
            event.setNextRetryAt(OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)).plusSeconds(delaySeconds));
            log.warn("Evento outbox {} no pudo ser publicado (intento {}/{}); programado reintento: {}",
                    event.getEventId(), nextRetry, properties.getMaxRetries(), reason);
        }
        outboxRepository.save(event);
    }
}
