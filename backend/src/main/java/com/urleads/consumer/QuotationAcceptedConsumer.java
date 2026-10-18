package com.urleads.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.urleads.config.RabbitMQConfig;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.repository.ProcessedEventRepository;
import com.urleads.service.QuotationEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class QuotationAcceptedConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuotationAcceptedConsumer.class);
    public static final int MAX_RETRIES = 3;
    public static final String HEADER_RETRIES = "x-retries";

    private final ProcessedEventRepository processedEventRepository;
    private final QuotationEventProcessor quotationEventProcessor;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public QuotationAcceptedConsumer(
            @Autowired(required = false) ProcessedEventRepository processedEventRepository,
            @Autowired(required = false) QuotationEventProcessor quotationEventProcessor,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.quotationEventProcessor = quotationEventProcessor;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_QUOTATION_ACCEPTED,
            containerFactory = "rabbitListenerContainerFactory",
            ackMode = "MANUAL"
    )
    public void onQuotationAccepted(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        String eventId = null;

        try {
            // 1. Deserializar el evento
            QuotationAcceptedEvent event = parseEvent(message);
            if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
                log.error("Mensaje rechazado: payload nulo o sin eventId (Poison Pill). Enviando a DLQ.");
                sendDirectToDlq(message, channel, deliveryTag, "PAYLOAD_INVALIDO");
                return;
            }

            eventId = event.getEventId();

            // 2. Control de idempotencia y descarte de duplicados
            if (processedEventRepository != null && processedEventRepository.existsByEventId(eventId)) {
                log.info("Evento con eventId '{}' ya fue procesado previamente. Descartando y emitiendo ACK inmediato.", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. Procesar en transacción SQL atómica (PostgreSQL)
            if (quotationEventProcessor != null) {
                quotationEventProcessor.procesarEventoCotizacionAceptada(event);
            }

            // 4. Confirmar ACK manual al broker únicamente tras commit SQL exitoso
            log.info("Evento {} procesado y persistido con éxito. Emitiendo ACK manual (tag={}).", eventId, deliveryTag);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Fallo al procesar evento {}: {}. Gestionando reintentos/DLQ...", eventId, ex.getMessage(), ex);
            handleFailure(message, channel, deliveryTag, eventId, ex);
        }
    }

    private QuotationAcceptedEvent parseEvent(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), QuotationAcceptedEvent.class);
        } catch (Exception e) {
            log.warn("Error al deserializar evento RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    private void handleFailure(
            Message message,
            Channel channel,
            long deliveryTag,
            String eventId,
            Exception ex
    ) throws IOException {
        int currentAttempt = getRetryCount(message);

        if (currentAttempt < MAX_RETRIES) {
            int nextAttempt = currentAttempt + 1;
            log.warn("Reintentando evento {} (intento {}/{})...", eventId, nextAttempt, MAX_RETRIES);

            // Reintentar publicando con header de intento actualizado
            if (rabbitTemplate != null) {
                MessageProperties properties = message.getMessageProperties();
                properties.setHeader(HEADER_RETRIES, nextAttempt);
                rabbitTemplate.send(
                        RabbitMQConfig.EXCHANGE_EVENTS,
                        RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED,
                        message
                );
            }

            // ACK a la entrega actual para no bloquear el canal
            channel.basicAck(deliveryTag, false);
        } else {
            log.error("Superado el límite de {} intentos para evento {}. Enrutando a Dead Letter Queue.", MAX_RETRIES, eventId);
            sendDirectToDlq(message, channel, deliveryTag, ex.getMessage());
        }
    }

    private void sendDirectToDlq(
            Message message,
            Channel channel,
            long deliveryTag,
            String reason
    ) throws IOException {
        // Enrutamiento explícito al DLX y DLQ si rabbitTemplate está disponible
        if (rabbitTemplate != null) {
            MessageProperties props = message.getMessageProperties();
            props.setHeader("x-exception-message", reason);
            rabbitTemplate.send(
                    RabbitMQConfig.EXCHANGE_DEAD_LETTER,
                    RabbitMQConfig.ROUTING_KEY_DLQ,
                    message
            );
        }

        // basicReject(requeue = false) activa el Dead Letter Exchange nativo de RabbitMQ en la cola
        channel.basicReject(deliveryTag, false);
    }

    private int getRetryCount(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            return 1;
        }
        Object header = message.getMessageProperties().getHeaders().get(HEADER_RETRIES);
        if (header instanceof Integer num) {
            return num;
        }
        if (header instanceof Number num) {
            return num.intValue();
        }
        return 1;
    }
}
