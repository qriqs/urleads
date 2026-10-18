package com.urleads.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {

    private final RabbitMQConfig rabbitMQConfig = new RabbitMQConfig();

    @Test
    @DisplayName("Verifica topología RabbitMQ: colas durables, DLQ y routing keys canónicos")
    void verificaTopologiaRabbitMQ() {
        // Exchange principal y DLX
        TopicExchange eventsExchange = rabbitMQConfig.eventsExchange();
        TopicExchange deadLetterExchange = rabbitMQConfig.deadLetterExchange();

        assertEquals("urleads.events.v1", eventsExchange.getName());
        assertTrue(eventsExchange.isDurable());

        assertEquals("urleads.dead.v1", deadLetterExchange.getName());
        assertTrue(deadLetterExchange.isDurable());

        // Cola principal con argumentos DLX
        Queue mainQueue = rabbitMQConfig.quotationAcceptedQueue();
        assertEquals("agenda.quotation-accepted.v1", mainQueue.getName());
        assertTrue(mainQueue.isDurable());
        assertEquals("urleads.dead.v1", mainQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("agenda.quotation-accepted.failed.v1", mainQueue.getArguments().get("x-dead-letter-routing-key"));

        // Cola DLQ
        Queue dlq = rabbitMQConfig.quotationAcceptedDlq();
        assertEquals("agenda.quotation-accepted.dlq", dlq.getName());
        assertTrue(dlq.isDurable());

        // Bindings
        Binding binding1 = rabbitMQConfig.quotationAcceptedBinding(mainQueue, eventsExchange);
        assertEquals("quotation.accepted.v1", binding1.getRoutingKey());

        Binding bindingAlias = rabbitMQConfig.quotationAcceptedAliasBinding(mainQueue, eventsExchange);
        assertEquals("quote.accepted.v1", bindingAlias.getRoutingKey());

        Binding dlqBinding = rabbitMQConfig.quotationAcceptedDlqBinding(dlq, deadLetterExchange);
        assertEquals("agenda.quotation-accepted.failed.v1", dlqBinding.getRoutingKey());
    }
}
