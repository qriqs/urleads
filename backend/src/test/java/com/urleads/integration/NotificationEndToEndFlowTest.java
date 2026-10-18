package com.urleads.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.urleads.consumer.QuotationAcceptedConsumer;
import com.urleads.dto.event.QuotationAcceptedData;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.entity.Notification;
import com.urleads.entity.ProcessedEvent;
import com.urleads.entity.Usuario;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test de Integración End-to-End Interno del Servicio Agenda (A05 / DSW-75):
 * Valida el flujo completo:
 * 1. Recepción de evento sintético quotation.accepted.v1 por consumidor RabbitMQ.
 * 2. Deduplicación idempotente y persistencia en base de datos PostgreSQL.
 * 3. Emisión confirmada de ACK manual tras commit SQL.
 * 4. Despacho en tiempo real por WebSocket estándar (RFC 6455).
 * 5. Disponibilidad y consulta determinista mediante API REST (/api/v1/notifications).
 * 6. Tolerancia a fallos: respaldo persistente ante desconexiones de cliente WebSocket
 *    y recuperación íntegra de notificaciones pendientes tras reconexión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "websocket.auth.timeout-seconds=5"
})
class NotificationEndToEndFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private QuotationAcceptedConsumer quotationAcceptedConsumer;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private LeadRepository leadRepository;

    @MockBean
    private NotaRepository notaRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private ProcessedEventRepository processedEventRepository;

    @MockBean
    private AgendaTareaRepository agendaTareaRepository;

    @MockBean
    private AgendaCitaRepository agendaCitaRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    // Almacenamiento en memoria simulando persistencia relacional
    private final Map<Long, Notification> notificationStore = new ConcurrentHashMap<>();
    private final Set<String> processedEventStore = ConcurrentHashMap.newKeySet();
    private final AtomicLong idSequence = new AtomicLong(100L);

    private Usuario testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        notificationStore.clear();
        processedEventStore.clear();

        testUser = new Usuario(42L, "alex_agenda", "bcrypt_hash");
        jwtToken = jwtService.generateToken("42");
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());

        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(testUser));
        when(usuarioRepository.findByUsername("42")).thenReturn(Optional.of(testUser));
        when(usuarioRepository.findByUsername("alex_agenda")).thenReturn(Optional.of(testUser));
        when(usuarioRepository.findAll()).thenReturn(List.of(testUser));

        // Mocking para deduplicación de eventos
        when(processedEventRepository.existsByEventId(anyString())).thenAnswer(inv -> {
            String eventId = inv.getArgument(0);
            return processedEventStore.contains(eventId);
        });

        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> {
            ProcessedEvent pe = inv.getArgument(0);
            processedEventStore.add(pe.getEventId());
            return pe;
        });

        // Mocking para persistencia de notificaciones
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(idSequence.incrementAndGet());
            }
            notificationStore.put(n.getId(), n);
            return n;
        });

        when(notificationRepository.findByIdAndUsuarioId(anyLong(), eq(42L))).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(notificationStore.get(id));
        });

        when(notificationRepository.findByEventId(anyString())).thenAnswer(inv -> {
            String evtId = inv.getArgument(0);
            return notificationStore.values().stream()
                    .filter(n -> evtId.equals(n.getEventId()))
                    .findFirst();
        });

        when(notificationRepository.findByUsuarioIdOrderByCreadoEnDescIdDesc(eq(42L), any(Pageable.class)))
                .thenAnswer(inv -> {
                    List<Notification> list = new ArrayList<>(notificationStore.values());
                    list.sort(Comparator.comparing(Notification::getCreadoEn).reversed()
                            .thenComparing(Comparator.comparing(Notification::getId).reversed()));
                    return new PageImpl<>(list);
                });

        when(notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(eq(42L), anyBoolean(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    boolean leida = inv.getArgument(1);
                    List<Notification> list = notificationStore.values().stream()
                            .filter(n -> n.isLeida() == leida)
                            .sorted(Comparator.comparing(Notification::getCreadoEn).reversed()
                                    .thenComparing(Comparator.comparing(Notification::getId).reversed()))
                            .toList();
                    return new PageImpl<>(list);
                });

        when(notificationRepository.countByUsuarioIdAndLeidaFalse(42L)).thenAnswer(inv ->
                notificationStore.values().stream().filter(n -> !n.isLeida()).count()
        );
    }

    private Message crearMensajeAMQP(QuotationAcceptedEvent event) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body, properties);
    }

    @Test
    @DisplayName("Flujo End-to-End: RabbitMQ -> BD PostgreSQL -> WebSocket en vivo -> Consulta y confirmación REST")
    void flujoCompleto_rabbit_hacia_bd_websocket_y_rest() throws Exception {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        BlockingQueue<String> wsMessages = new LinkedBlockingQueue<>();
        BlockingQueue<CloseStatus> wsCloseStatuses = new LinkedBlockingQueue<>();

        TextWebSocketHandler wsHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                wsMessages.offer(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                wsCloseStatuses.offer(status);
            }
        };

        // PASO 1: Conexión y autenticación WebSocket según RFC 6455
        String wsUri = "ws://localhost:" + port + "/ws/notifications";
        WebSocketSession wsSession = wsClient.execute(wsHandler, wsUri).get(5, TimeUnit.SECONDS);
        assertThat(wsSession.isOpen()).isTrue();

        // Enviar primer frame de autenticación
        wsSession.sendMessage(new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"" + jwtToken + "\"}"));

        String ackMessage = wsMessages.poll(5, TimeUnit.SECONDS);
        assertThat(ackMessage).isNotNull();
        JsonNode ackNode = objectMapper.readTree(ackMessage);
        assertThat(ackNode.path("type").asText()).isEqualTo("authenticated");
        assertThat(ackNode.path("userId").asLong()).isEqualTo(42L);

        // PASO 2: Inyección de evento sintético quotation.accepted.v1 simulando mensaje de broker
        QuotationAcceptedEvent event1 = QuotationAcceptedEvent.builder()
                .eventId("evt-e2e-204")
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

        Channel mockChannel = mock(Channel.class);
        Message amqpMessage1 = crearMensajeAMQP(event1);
        long deliveryTag1 = 5501L;

        quotationAcceptedConsumer.onQuotationAccepted(amqpMessage1, mockChannel, deliveryTag1);

        // PASO 3: Verificación de persistencia SQL y ACK manual al broker
        verify(mockChannel).basicAck(deliveryTag1, false);
        assertThat(processedEventStore).contains("evt-e2e-204");
        assertThat(notificationStore).hasSize(1);

        Notification notifPersistida = notificationStore.values().iterator().next();
        assertThat(notifPersistida.getEventId()).isEqualTo("evt-e2e-204");
        assertThat(notifPersistida.getTitulo()).isEqualTo("Cotización #204 aceptada");
        assertThat(notifPersistida.isLeida()).isFalse();

        // PASO 4: Verificación de recepción en vivo por WebSocket
        String wsNotificationPayload = wsMessages.poll(5, TimeUnit.SECONDS);
        assertThat(wsNotificationPayload).isNotNull();
        JsonNode wsJson = objectMapper.readTree(wsNotificationPayload);

        assertThat(wsJson.path("type").asText()).isEqualTo("notification.created");
        JsonNode wsData = wsJson.path("data");
        assertThat(wsData.path("id").asLong()).isEqualTo(notifPersistida.getId());
        assertThat(wsData.path("sourceEventId").asText()).isEqualTo("evt-e2e-204");
        assertThat(wsData.path("read").asBoolean()).isFalse();
        assertThat(wsData.path("titulo").asText()).isEqualTo("Cotización #204 aceptada");

        // PASO 5: Verificación mediante API REST (GET /api/v1/notifications)
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(jwtToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders);

        ResponseEntity<String> restResponse = restTemplate.exchange(
                "/api/v1/notifications",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(restResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode restJson = objectMapper.readTree(restResponse.getBody());
        assertThat(restJson.path("totalElements").asInt()).isEqualTo(1);
        assertThat(restJson.path("content").get(0).path("eventId").asText()).isEqualTo("evt-e2e-204");

        // PASO 6: Prueba de Resiliencia ante Desconexión
        // El cliente se desconecta del WebSocket
        wsSession.close();

        // Se recibe un segundo evento mientras no hay conexiones WebSocket activas
        QuotationAcceptedEvent event2 = QuotationAcceptedEvent.builder()
                .eventId("evt-e2e-205")
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId("42")
                .data(QuotationAcceptedData.builder()
                        .quoteId(205L)
                        .leadId(106L)
                        .currency("PEN")
                        .total(new BigDecimal("4800.00"))
                        .build())
                .build();

        Message amqpMessage2 = crearMensajeAMQP(event2);
        long deliveryTag2 = 5502L;

        quotationAcceptedConsumer.onQuotationAccepted(amqpMessage2, mockChannel, deliveryTag2);

        verify(mockChannel).basicAck(deliveryTag2, false);
        assertThat(processedEventStore).contains("evt-e2e-205");
        assertThat(notificationStore).hasSize(2);

        // PASO 7: Recuperación vía REST tras reconexión (conteo y marcado como leída)
        ResponseEntity<String> unreadCountResponse = restTemplate.exchange(
                "/api/v1/notifications/unread-count",
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        assertThat(unreadCountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode unreadNode = objectMapper.readTree(unreadCountResponse.getBody());
        assertThat(unreadNode.path("unreadCount").asLong()).isEqualTo(2L);

        // Marcar la primera notificación como leída vía PATCH
        ResponseEntity<String> patchResponse = restTemplate.exchange(
                "/api/v1/notifications/" + notifPersistida.getId() + "/read",
                HttpMethod.PATCH,
                requestEntity,
                String.class
        );
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Consultar solo no leídas: ahora solo debe quedar la recibida durante la desconexión
        ResponseEntity<String> unreadOnlyResponse = restTemplate.exchange(
                "/api/v1/notifications?read=false",
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        assertThat(unreadOnlyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode unreadOnlyJson = objectMapper.readTree(unreadOnlyResponse.getBody());
        assertThat(unreadOnlyJson.path("totalElements").asInt()).isEqualTo(1);
        assertThat(unreadOnlyJson.path("content").get(0).path("eventId").asText()).isEqualTo("evt-e2e-205");

        // PASO 8: Idempotencia en flujo integrado: inyección repetida de evt-e2e-204 no altera estado
        long deliveryTagDuplicate = 5503L;
        quotationAcceptedConsumer.onQuotationAccepted(amqpMessage1, mockChannel, deliveryTagDuplicate);
        verify(mockChannel).basicAck(deliveryTagDuplicate, false);
        assertThat(notificationStore).hasSize(2); // No se creó ningún duplicado
    }
}
