package com.urleads.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.config.RabbitMQConfig;
import com.urleads.dto.event.QuotationAcceptedData;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.entity.Notification;
import com.urleads.entity.Usuario;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.ProcessedEventRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea A15 (DSW-85): Integración real de Agenda con RabbitMQ, Eureka y PostgreSQL.
 * Valida:
 * 1. Registro activo y heartbeat del microservicio en Eureka Server bajo el nombre 'agenda-service'.
 * 2. Consumo y procesamiento real de eventos canónicos 'quotation.accepted.v1' publicados por Comercial.
 * 3. Deduplicación e idempotencia basada en eventId sobre base de datos PostgreSQL real.
 * 4. Despacho WebSocket estándar en tiempo real, tolerancia a desconexión y reconexión en caliente.
 * 5. Persistencia y recuperación íntegra de notificaciones offline vía API REST (/api/v1/notifications).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.application.name=agenda-service",
        "eureka.client.enabled=true",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
        "eureka.client.register-with-eureka=true",
        "eureka.client.fetch-registry=false",
        "eureka.instance.prefer-ip-address=true",
        "eureka.instance.lease-renewal-interval-in-seconds=2",
        "eureka.instance.lease-expiration-duration-in-seconds=4",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "websocket.auth.timeout-seconds=5"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgendaIntegrationRealIT {

    @LocalServerPort
    private int port;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    private Usuario testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                isServiceAvailable("localhost", 8761) && isServiceAvailable("localhost", 5672),
                "Saltando AgendaIntegrationRealIT: requiere Eureka Server (8761) y RabbitMQ (5672) activos (C14 Compose)."
        );
        testUser = usuarioRepository.findByUsername("sebas")
                .orElseGet(() -> usuarioRepository.save(new Usuario("sebas", "$2a$10$abcdefghijklmnopqrstuvwxyz123456")));
        jwtToken = jwtService.generateToken(testUser.getUsername());
    }

    private static boolean isServiceAvailable(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Criterio 1: Demostrar registro activo de 'agenda-service' en Eureka Server (puerto 8761)")
    void test01_eurekaRegistration_agendaServiceIsRegisteredAndActive() throws Exception {
        RestTemplate eurekaHttp = new RestTemplate();
        String eurekaUrl = "http://localhost:8761/eureka/apps/AGENDA-SERVICE";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        boolean registered = false;
        String responseBody = "";

        // Esperar hasta 20 segundos a que Eureka procese el registro del cliente
        for (int i = 0; i < 20; i++) {
            try {
                ResponseEntity<String> response = eurekaHttp.exchange(eurekaUrl, HttpMethod.GET, request, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    responseBody = response.getBody();
                    if (responseBody.contains("AGENDA-SERVICE") && (responseBody.contains("UP") || responseBody.contains("STARTING"))) {
                        registered = true;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }

        assertThat(registered)
                .as("El microservicio debe estar registrado activamente en Eureka Server bajo 'agenda-service'")
                .isTrue();

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode appNode = root.path("application");
        assertThat(appNode.path("name").asText()).isEqualToIgnoringCase("AGENDA-SERVICE");
    }

    @Test
    @Order(2)
    @DisplayName("Criterio 2: Procesar evento canónico real quotation.accepted.v1 de Comercial vía RabbitMQ y deduplicar")
    void test02_rabbitMqEventProcessing_quotationAcceptedProcessedAndDeduplicated() throws Exception {
        String eventId = UUID.randomUUID().toString();
        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId(eventId)
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId(String.valueOf(testUser.getId()))
                .data(QuotationAcceptedData.builder()
                        .quoteId(701L)
                        .leadId(105L)
                        .currency("USD")
                        .total(new BigDecimal("2450.00"))
                        .build())
                .build();

        byte[] payload = objectMapper.writeValueAsBytes(event);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(payload, properties);

        // Publicar al exchange canónico urleads.events.v1
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED, message);

        // Esperar a que el consumidor procese y persista en PostgreSQL
        Notification savedNotification = null;
        for (int i = 0; i < 15; i++) {
            var list = notificationRepository.findAll();
            var found = list.stream().filter(n -> eventId.equals(n.getEventId())).findFirst();
            if (found.isPresent()) {
                savedNotification = found.get();
                break;
            }
            Thread.sleep(1000);
        }

        assertThat(savedNotification).isNotNull();
        assertThat(savedNotification.getTitulo()).contains("Cotización #701 aceptada");
        assertThat(savedNotification.getMensaje()).contains("lead #105", "USD 2450.00");
        assertThat(savedNotification.isLeida()).isFalse();
        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();

        // Enviar nuevamente el mismo evento para verificar deduplicación e idempotencia
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED, message);
        Thread.sleep(2000);

        long count = notificationRepository.findAll().stream()
                .filter(n -> eventId.equals(n.getEventId()))
                .count();
        assertThat(count).as("El evento duplicado no debe generar una segunda notificación").isEqualTo(1L);
    }

    @Test
    @Order(3)
    @DisplayName("Criterio 3: Despacho en tiempo real por WebSocket, reconexión en caliente y disponibilidad offline en REST")
    void test03_webSocketHotReconnectAndOfflineRestDelivery() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws/notifications";
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messagesQueue = new LinkedBlockingQueue<>();

        // 1. Conexión 1: Conectar WebSocket y autenticar con primer frame
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messagesQueue.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();
        session.sendMessage(new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"" + jwtToken + "\"}"));

        String authResponse = messagesQueue.poll(4, TimeUnit.SECONDS);
        assertThat(authResponse).contains("authenticated");

        // 2. Publicar evento con cliente conectado (entrega en vivo)
        String liveEventId = UUID.randomUUID().toString();
        QuotationAcceptedEvent liveEvent = QuotationAcceptedEvent.builder()
                .eventId(liveEventId)
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId(String.valueOf(testUser.getId()))
                .data(QuotationAcceptedData.builder()
                        .quoteId(702L)
                        .leadId(106L)
                        .currency("USD")
                        .total(new BigDecimal("1200.00"))
                        .build())
                .build();

        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED,
                new Message(objectMapper.writeValueAsBytes(liveEvent), new MessageProperties()));

        String liveWsMessage = messagesQueue.poll(8, TimeUnit.SECONDS);
        assertThat(liveWsMessage).isNotNull();
        assertThat(liveWsMessage).contains("notification.created", liveEventId, "Cotización #702 aceptada");

        // 3. Simular caída de conexión / reinicio de contenedor
        session.close(CloseStatus.NORMAL);
        Thread.sleep(1000);

        // 4. Mientras el cliente está desconectado, Comercial acepta otra cotización (#703)
        String offlineEventId = UUID.randomUUID().toString();
        QuotationAcceptedEvent offlineEvent = QuotationAcceptedEvent.builder()
                .eventId(offlineEventId)
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId(String.valueOf(testUser.getId()))
                .data(QuotationAcceptedData.builder()
                        .quoteId(703L)
                        .leadId(107L)
                        .currency("USD")
                        .total(new BigDecimal("4800.00"))
                        .build())
                .build();

        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED,
                new Message(objectMapper.writeValueAsBytes(offlineEvent), new MessageProperties()));

        // Esperar persistencia en PostgreSQL
        Notification offlineNotification = null;
        for (int i = 0; i < 15; i++) {
            var found = notificationRepository.findAll().stream().filter(n -> offlineEventId.equals(n.getEventId())).findFirst();
            if (found.isPresent()) {
                offlineNotification = found.get();
                break;
            }
            Thread.sleep(1000);
        }
        assertThat(offlineNotification).isNotNull();

        // 5. Reconexión en caliente del WebSocket
        BlockingQueue<String> reconnectedQueue = new LinkedBlockingQueue<>();
        WebSocketSession reconnectedSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                reconnectedQueue.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        assertThat(reconnectedSession.isOpen()).isTrue();
        reconnectedSession.sendMessage(new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"" + jwtToken + "\"}"));
        String reconnectedAuth = reconnectedQueue.poll(4, TimeUnit.SECONDS);
        assertThat(reconnectedAuth).contains("authenticated");

        // 6. Consultar notificaciones pendientes vía REST (/api/v1/notifications)
        HttpHeaders restHeaders = new HttpHeaders();
        restHeaders.setBearerAuth(jwtToken);
        ResponseEntity<String> restResponse = restTemplate.exchange(
                "/api/v1/notifications?unreadOnly=false",
                HttpMethod.GET,
                new HttpEntity<>(restHeaders),
                String.class
        );

        assertThat(restResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restResponse.getBody()).contains(offlineEventId, "Cotización #703 aceptada");

        // 7. Marcar como leída vía REST
        Long notificationId = offlineNotification.getId();
        ResponseEntity<String> readResponse = restTemplate.exchange(
                "/api/v1/notifications/" + notificationId + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(restHeaders),
                String.class
        );
        assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Notification updated = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(updated.isLeida()).isTrue();

        reconnectedSession.close(CloseStatus.NORMAL);
    }

    @Test
    @Order(4)
    @DisplayName("Criterio 4: Manejo resiliente de poison pill / mensaje inválido y desvío a DLQ")
    void test04_rabbitMqDeadLetterQueue_poisonPillRoutedToDlq() throws Exception {
        // Enviar un mensaje corrupto sin eventId ni estructura válida
        byte[] poisonPill = "{\"invalid\":\"data-without-eventId\"}".getBytes();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(poisonPill, properties);

        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_QUOTATION_ACCEPTED, message);

        // Esperar a que el consumidor lo rechace y se enrute al DLQ
        Thread.sleep(3000);

        // Verificar que la cola de Dead Letter ha recibido mensajes
        Message dlqMessage = rabbitTemplate.receive(RabbitMQConfig.QUEUE_DLQ, 5000);
        assertThat(dlqMessage).as("El mensaje inválido debe enrutarse a la DLQ").isNotNull();
    }
}
