package com.urleads.publisher;

import com.urleads.config.OutboxProperties;
import com.urleads.entity.OutboxEvent;
import com.urleads.entity.OutboxStatus;
import com.urleads.event.CrmEventTypes;
import com.urleads.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("R3b/R4: Pruebas unitarias del publicador OutboxPublisher y publisher confirms")
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxProperties properties;
    private Clock fixedClock;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setConfirmTimeoutMs(1000);
        properties.setMaxRetries(3);
        properties.setBaseBackoffSeconds(5);
        properties.setBatchSize(20);

        fixedClock = Clock.fixed(Instant.parse("2026-10-17T23:30:00Z"), ZoneOffset.UTC);
        publisher = new OutboxPublisher(outboxRepository, rabbitTemplate, properties, fixedClock);
    }

    private OutboxEvent createSampleEvent(String eventType, String routingKey) {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setRoutingKey(routingKey);
        event.setExchange(CrmEventTypes.EXCHANGE);
        event.setPayload("{\"eventId\":\"" + event.getEventId() + "\",\"eventType\":\"" + eventType + "\"}");
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(OffsetDateTime.now(fixedClock));
        return event;
    }

    @Test
    @DisplayName("R3b: Confirmación positiva del broker marca el registro como DELIVERED con fecha de envío")
    void publishEvent_positiveConfirm_marksDelivered() {
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_CREATED, "lead.created.v1");

        // Simular confirmación positiva inmediata de RabbitMQ
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("urleads.events.v1"), eq("lead.created.v1"), any(Message.class), any(CorrelationData.class));

        boolean published = publisher.publishEvent(event);

        assertThat(published).isTrue();

        // Verificar propiedades del mensaje persistente
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("urleads.events.v1"), eq("lead.created.v1"), messageCaptor.capture(), any(CorrelationData.class));
        Message message = messageCaptor.getValue();
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());

        // Verificar actualización en outbox
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        assertThat(saved.getSentAt()).isEqualTo(OffsetDateTime.now(fixedClock));
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    @DisplayName("R4: Confirmación negativa (nack) retiene evento en PENDING, incrementa retry_count y programa backoff")
    void publishEvent_negativeConfirm_retainsForRetry() {
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_UPDATED, "lead.updated.v1");
        String originalEventId = event.getEventId();
        String originalPayload = event.getPayload();

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "Canal saturado"));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(), any());

        boolean published = publisher.publishEvent(event);

        assertThat(published).isFalse();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("Canal saturado");
        assertThat(saved.getNextRetryAt()).isNotNull();
        assertThat(saved.getNextRetryAt()).isAfter(saved.getCreatedAt());

        // R4: Preserva idéntico eventId y payload para semántica at-least-once
        assertThat(saved.getEventId()).isEqualTo(originalEventId);
        assertThat(saved.getPayload()).isEqualTo(originalPayload);
    }

    @Test
    @DisplayName("R4: Mensaje devuelto como no enrutable (basic.return) se detecta y registra para reintento")
    void publishEvent_unroutableReturn_recordsFailure() {
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_DELETED, "lead.deleted.v1");

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            Message msg = invocation.getArgument(2);
            cd.setReturned(new ReturnedMessage(msg, 312, "NO_ROUTE", "urleads.events.v1", "lead.deleted.v1"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(), any());

        boolean published = publisher.publishEvent(event);

        assertThat(published).isFalse();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("no enrutable").contains("NO_ROUTE");
    }

    @Test
    @DisplayName("R4: Expiración de tiempo de espera (timeout) en confirmación registra falla y programa reintento")
    void publishEvent_confirmTimeout_recordsFailure() {
        properties.setConfirmTimeoutMs(50); // Timeout corto para la prueba
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_CREATED, "lead.created.v1");

        // Futuro que no se completa (simula caída o latencia extrema del broker)
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            // No completamos el futuro
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(), any());

        boolean published = publisher.publishEvent(event);

        assertThat(published).isFalse();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("expiró");
    }

    @Test
    @DisplayName("R4: Excepción de conexión con el broker captura error y no rompe ejecución")
    void publishEvent_brokerConnectionException_recordsFailureGracefully() {
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_CREATED, "lead.created.v1");

        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).send(any(), any(), any(), any());

        boolean published = publisher.publishEvent(event);

        assertThat(published).isFalse();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("Connection refused");
    }

    @Test
    @DisplayName("R4: Reintentos acotados: al superar maxRetries el estado pasa a FAILED")
    void publishEvent_exceedsMaxRetries_transitionsToFailed() {
        OutboxEvent event = createSampleEvent(CrmEventTypes.LEAD_CREATED, "lead.created.v1");
        event.setRetryCount(2); // Máximo configurado es 3

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "Rechazo persistente"));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(), any());

        boolean published = publisher.publishEvent(event);

        assertThat(published).isFalse();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent saved = eventCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("publishPendingEvents: procesa lote de eventos pendientes y retorna cantidad entregada")
    void publishPendingEvents_processesBatchSuccessfully() {
        OutboxEvent ev1 = createSampleEvent(CrmEventTypes.LEAD_CREATED, "lead.created.v1");
        OutboxEvent ev2 = createSampleEvent(CrmEventTypes.LEAD_UPDATED, "lead.updated.v1");

        when(outboxRepository.findPendingForPublishing(any(), anyInt())).thenReturn(List.of(ev1, ev2));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(), any());

        int count = publisher.publishPendingEvents();

        assertThat(count).isEqualTo(2);
        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }
}
