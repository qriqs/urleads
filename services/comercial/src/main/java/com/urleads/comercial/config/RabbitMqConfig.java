package com.urleads.comercial.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "urleads.events.v1";
    public static final String QUOTATION_ACCEPTED_ROUTING_KEY = "quotation.accepted.v1";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter() {
            @Override
            protected Message createMessage(Object objectToConvert, MessageProperties messageProperties) {
                return createMessage(objectToConvert, messageProperties, null);
            }

            @Override
            protected Message createMessage(Object objectToConvert, MessageProperties messageProperties, Type genericType) {
                if (objectToConvert instanceof String str) {
                    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                    messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
                    messageProperties.setContentLength(bytes.length);
                    return new Message(bytes, messageProperties);
                }
                return super.createMessage(objectToConvert, messageProperties, genericType);
            }
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        return template;
    }
}
