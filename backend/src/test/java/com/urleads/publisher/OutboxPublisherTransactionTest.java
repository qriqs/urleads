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
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OutboxPublisherTransactionTest.TestConfig.class)
@DisplayName("Corrección attempt-2: Verificación del proxy transaccional y frontera de ejecución programada")
class OutboxPublisherTransactionTest {

    @Configuration
    @EnableTransactionManagement
    @EnableScheduling
    static class TestConfig {

        @Bean
        public OutboxRepository outboxRepository() {
            return Mockito.mock(OutboxRepository.class);
        }

        @Bean
        public RabbitTemplate rabbitTemplate() {
            return Mockito.mock(RabbitTemplate.class);
        }

        @Bean
        public OutboxProperties outboxProperties() {
            OutboxProperties props = new OutboxProperties();
            props.setEnabled(true);
            props.setBatchSize(20);
            props.setConfirmTimeoutMs(1000);
            props.setMaxRetries(3);
            props.setBaseBackoffSeconds(5);
            return props;
        }

        @Bean
        public Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-17T23:30:00Z"), ZoneOffset.UTC);
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            AbstractPlatformTransactionManager tm = new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
            return Mockito.spy(tm);
        }

        @Bean
        public OutboxPublisher outboxPublisher(OutboxRepository outboxRepository,
                                               RabbitTemplate rabbitTemplate,
                                               OutboxProperties outboxProperties,
                                               Clock testClock) {
            return new OutboxPublisher(outboxRepository, rabbitTemplate, outboxProperties, testClock);
        }
    }

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTaskHolder;

    private OutboxEvent sampleEvent;

    @BeforeEach
    void setUp() {
        reset(outboxRepository, rabbitTemplate, transactionManager);

        sampleEvent = new OutboxEvent();
        sampleEvent.setId(10L);
        sampleEvent.setEventId(UUID.randomUUID().toString());
        sampleEvent.setEventType(CrmEventTypes.LEAD_CREATED);
        sampleEvent.setRoutingKey("lead.created.v1");
        sampleEvent.setExchange(CrmEventTypes.EXCHANGE);
        sampleEvent.setPayload("{\"eventId\":\"" + sampleEvent.getEventId() + "\"}");
        sampleEvent.setStatus(OutboxStatus.PENDING);
        sampleEvent.setRetryCount(0);
        sampleEvent.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Mock para responder ack inmediato de RabbitMQ
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("urleads.events.v1"), eq("lead.created.v1"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("El publicador inyectado es un proxy AOP de Spring")
    void outboxPublisherIsInjectedAsSpringAopProxy() {
        assertThat(AopUtils.isAopProxy(publisher)).isTrue();
    }

    @Test
    @DisplayName("La invocación de scheduledPublish a través del proxy ejecuta con transacción activa durante query y guardado")
    void scheduledPublish_throughProxy_executesWithinActiveTransaction() {
        AtomicBoolean txActiveDuringQuery = new AtomicBoolean(false);
        AtomicBoolean txActiveDuringSave = new AtomicBoolean(false);

        when(outboxRepository.findPendingForPublishing(any(), anyInt())).thenAnswer(invocation -> {
            txActiveDuringQuery.set(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(sampleEvent);
        });

        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            txActiveDuringSave.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.getArgument(0);
        });

        // Invocación a través del proxy Spring
        publisher.scheduledPublish();

        // Evidencia concluyente: la transacción estuvo activa al reclamar las filas (FOR UPDATE SKIP LOCKED)
        assertThat(txActiveDuringQuery.get())
                .as("La consulta FOR UPDATE SKIP LOCKED debe ejecutarse dentro de la transacción activa")
                .isTrue();

        // Evidencia concluyente: la transacción permaneció activa hasta registrar el resultado de confirmación
        assertThat(txActiveDuringSave.get())
                .as("El guardado del estado de confirmación debe ejecutarse dentro de la misma transacción activa")
                .isTrue();

        // Verificamos que el TransactionManager de Spring confirmó la transacción al finalizar el método programado
        verify(transactionManager, atLeastOnce()).commit(any());
    }

    @Test
    @DisplayName("La tarea registrada por @Scheduled de Spring se invoca sobre el proxy y ejecuta con transacción activa")
    void scheduledTaskRunnable_registeredBySpring_executesOnProxyWithActiveTransaction() {
        Set<ScheduledTask> tasks = scheduledTaskHolder.getScheduledTasks();
        assertThat(tasks).isNotEmpty();

        ScheduledTask scheduledTask = tasks.stream()
                .filter(t -> t.getTask().getRunnable().toString().contains("scheduledPublish"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la tarea programada para scheduledPublish"));

        AtomicBoolean txActiveDuringScheduledRun = new AtomicBoolean(false);

        when(outboxRepository.findPendingForPublishing(any(), anyInt())).thenAnswer(invocation -> {
            txActiveDuringScheduledRun.set(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(sampleEvent);
        });

        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Ejecutar el runnable real registrado por el scheduler de Spring
        scheduledTask.getTask().getRunnable().run();

        // Evidencia de que el scheduler de Spring invoca a través del proxy con transacción activa
        assertThat(txActiveDuringScheduledRun.get())
                .as("El runnable registrado por @Scheduled debe ejecutar con transacción activa")
                .isTrue();

        verify(transactionManager, atLeastOnce()).commit(any());
    }

    @Test
    @DisplayName("Falla inesperada de base de datos en scheduledPublish provoca rollback de la transacción")
    void scheduledPublish_databaseException_triggersTransactionRollback() {
        when(outboxRepository.findPendingForPublishing(any(), anyInt()))
                .thenThrow(new RuntimeException("Falla simulada de conexión a PostgreSQL"));

        assertThatThrownBy(() -> publisher.scheduledPublish())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PostgreSQL");

        verify(transactionManager, atLeastOnce()).rollback(any());
    }
}
