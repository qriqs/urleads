package com.urleads.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.urleads.config.RabbitMQConfig;
import com.urleads.dto.event.QuotationAcceptedData;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.entity.Notification;
import com.urleads.entity.ProcessedEvent;
import com.urleads.entity.Usuario;
import com.urleads.repository.ProcessedEventRepository;
import com.urleads.service.QuotationEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationAcceptedConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private QuotationEventProcessor quotationEventProcessor;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private QuotationAcceptedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new QuotationAcceptedConsumer(
                processedEventRepository,
                quotationEventProcessor,
                rabbitTemplate,
                objectMapper
        );
    }

    private Message crearMensajeSintetico(QuotationAcceptedEvent event, Integer retryCount) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (retryCount != null) {
            properties.setHeader(QuotationAcceptedConsumer.HEADER_RETRIES, retryCount);
        }
        return new Message(body, properties);
    }

    @Test
    @DisplayName("Evento válido: persiste en PostgreSQL y emite ACK manual al broker")
    void eventoValido_persisteEnBdYEmitenAckManual() throws Exception {
        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-quote-204")
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.parse("2026-10-17T23:30:00Z"))
                .ownerId("42")
                .data(QuotationAcceptedData.builder()
                        .quoteId(204L)
                        .leadId(105L)
                        .currency("USD")
                        .total(new BigDecimal("1250.00"))
                        .build())
                .build();

        Message message = crearMensajeSintetico(event, null);
        long deliveryTag = 1001L;

        when(processedEventRepository.existsByEventId("evt-quote-204")).thenReturn(false);
        when(quotationEventProcessor.procesarEventoCotizacionAceptada(any())).thenReturn(
                Notification.builder().id(1L).titulo("Cotización #204 aceptada").build()
        );

        consumer.onQuotationAccepted(message, channel, deliveryTag);

        verify(processedEventRepository).existsByEventId("evt-quote-204");
        verify(quotationEventProcessor).procesarEventoCotizacionAceptada(any(QuotationAcceptedEvent.class));
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Deduplicación idempotente: evento repetido se descarta y emite ACK inmediato")
    void eventoDuplicado_seDescartaYEmitenAckInmediato() throws Exception {
        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-duplicate-888")
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId("42")
                .data(QuotationAcceptedData.builder().quoteId(300L).build())
                .build();

        Message message = crearMensajeSintetico(event, null);
        long deliveryTag = 2002L;

        when(processedEventRepository.existsByEventId("evt-duplicate-888")).thenReturn(true);

        consumer.onQuotationAccepted(message, channel, deliveryTag);

        verify(processedEventRepository).existsByEventId("evt-duplicate-888");
        // No debe procesar de nuevo en la base de datos
        verify(quotationEventProcessor, never()).procesarEventoCotizacionAceptada(any());
        // Debe confirmar ACK inmediato para sacar el mensaje de la cola
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Poison pill / Payload inválido: se rechaza directamente a DLQ sin reintentar")
    void payloadInvalido_seRechazaDirectamenteADlq() throws Exception {
        Message invalidMessage = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        long deliveryTag = 3003L;

        consumer.onQuotationAccepted(invalidMessage, channel, deliveryTag);

        // Debe rechazar con requeue=false (activa enrutamiento al DLX nativo)
        verify(channel).basicReject(deliveryTag, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(quotationEventProcessor, never()).procesarEventoCotizacionAceptada(any());
    }

    @Test
    @DisplayName("Fallo transitorio en intento 1: incrementa reintento a 2 y republica a la cola")
    void falloTransitorio_intento1_republicaParaReintento() throws Exception {
        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-error-1")
                .eventType("quotation.accepted.v1")
                .ownerId("42")
                .data(QuotationAcceptedData.builder().quoteId(500L).build())
                .build();

        Message message = crearMensajeSintetico(event, 1);
        long deliveryTag = 4004L;

        when(processedEventRepository.existsByEventId("evt-error-1")).thenReturn(false);
        doThrow(new RuntimeException("Error temporal de conexión a BD"))
                .when(quotationEventProcessor).procesarEventoCotizacionAceptada(any());

        consumer.onQuotationAccepted(message, channel, deliveryTag);

        // Verifica que se republicó con intento = 2
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED),
                messageCaptor.capture()
        );
        assertEquals(2, messageCaptor.getValue().getMessageProperties().getHeaders().get(QuotationAcceptedConsumer.HEADER_RETRIES));

        // Hace ACK de la entrega actual para no duplicar entrega en paralelo
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Fallo tras 3 intentos: enruta a DLQ y rechaza sin reencolar")
    void fallosAgotados_intento3_enrutaADlq() throws Exception {
        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-error-3")
                .eventType("quotation.accepted.v1")
                .ownerId("42")
                .data(QuotationAcceptedData.builder().quoteId(700L).build())
                .build();

        Message message = crearMensajeSintetico(event, 3);
        long deliveryTag = 5005L;

        when(processedEventRepository.existsByEventId("evt-error-3")).thenReturn(false);
        doThrow(new RuntimeException("Fallo crítico persistente"))
                .when(quotationEventProcessor).procesarEventoCotizacionAceptada(any());

        consumer.onQuotationAccepted(message, channel, deliveryTag);

        // Envía explícitamente a exchange DLQ
        verify(rabbitTemplate).send(
                eq(RabbitMQConfig.EXCHANGE_DEAD_LETTER),
                eq(RabbitMQConfig.ROUTING_KEY_DLQ),
                any(Message.class)
        );

        // Rechaza con requeue=false
        verify(channel).basicReject(deliveryTag, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
