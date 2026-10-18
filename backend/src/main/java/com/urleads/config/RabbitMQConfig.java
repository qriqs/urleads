package com.urleads.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnClass(ConnectionFactory.class)
public class RabbitMQConfig {

    // Exchanges
    public static final String EXCHANGE_EVENTS = "urleads.events.v1";
    public static final String EXCHANGE_DEAD_LETTER = "urleads.dead.v1";

    // Routing Keys
    public static final String ROUTING_KEY_QUOTATION_ACCEPTED = "quotation.accepted.v1";
    public static final String ROUTING_KEY_QUOTE_ACCEPTED_ALIAS = "quote.accepted.v1";
    public static final String ROUTING_KEY_DLQ = "agenda.quotation-accepted.failed.v1";

    // Queues
    public static final String QUEUE_QUOTATION_ACCEPTED = "agenda.quotation-accepted.v1";
    public static final String QUEUE_DLQ = "agenda.quotation-accepted.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_DEAD_LETTER).durable(true).build();
    }

    @Bean
    public Queue quotationAcceptedQueue() {
        return QueueBuilder.durable(QUEUE_QUOTATION_ACCEPTED)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue quotationAcceptedDlq() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding quotationAcceptedBinding(
            @Qualifier("quotationAcceptedQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_QUOTATION_ACCEPTED);
    }

    @Bean
    public Binding quotationAcceptedAliasBinding(
            @Qualifier("quotationAcceptedQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_QUOTE_ACCEPTED_ALIAS);
    }

    @Bean
    public Binding quotationAcceptedDlqBinding(
            @Qualifier("quotationAcceptedDlq") Queue queue,
            @Qualifier("deadLetterExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_DLQ);
    }

    @Bean(name = "agendaJsonMessageConverter")
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.listener.simple.auto-startup:false}")
    private boolean autoStartup;

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("agendaJsonMessageConverter") MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
