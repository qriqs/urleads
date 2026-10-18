package com.urleads.config;

import com.urleads.event.CrmEventTypes;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para el CRM (Backend V2 C04).
 * Declara el exchange de tópicos canónico durable urleads.events.v1 e ignora
 * excepciones de declaración si el broker no está disponible durante el arranque.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange urleadsEventsExchange() {
        return ExchangeBuilder.topicExchange(CrmEventTypes.EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }
}
