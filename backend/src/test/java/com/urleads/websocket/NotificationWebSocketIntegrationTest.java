package com.urleads.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "websocket.auth.timeout-seconds=4"
})
class NotificationWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

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
    private RecordatorioRepository recordatorioRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("Handshake con StandardWebSocketClient, autenticación con token sintético y recepción en vivo de notificación")
    void standardWebSocketClient_authenticatesAndReceivesLiveNotification() throws Exception {
        Usuario usuario = new Usuario(99L, "alex_ws", "hash");
        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("alex_ws")).thenReturn(Optional.of(usuario));

        // Generar token JWT sintético válido con sub = 99
        String token = jwtService.generateToken("99");

        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
        BlockingQueue<CloseStatus> closeStatuses = new LinkedBlockingQueue<>();

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessages.offer(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeStatuses.offer(status);
            }
        };

        String wsUri = "ws://localhost:" + port + "/ws/notifications";
        WebSocketSession session = client.execute(clientHandler, wsUri).get(5, TimeUnit.SECONDS);
        assertThat(session.isOpen()).isTrue();

        // 1. Enviar frame de autenticación según contrato { type: "authenticate", accessToken: "..." }
        String authFrame = String.format("{\"type\":\"authenticate\",\"accessToken\":\"%s\"}", token);
        session.sendMessage(new TextMessage(authFrame));

        // 2. Esperar confirmación de autenticación
        String authAck = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(authAck).isNotNull();
        JsonNode ackJson = objectMapper.readTree(authAck);
        assertThat(ackJson.path("type").asText()).isEqualTo("authenticated");
        assertThat(ackJson.path("userId").asLong()).isEqualTo(99L);

        // 3. Emitir notificación hacia el usuario autenticado
        NotificationResponse notif = NotificationResponse.builder()
                .id(204L)
                .eventId("evt-ws-live-101")
                .tipo("COTIZACION_ACEPTADA")
                .titulo("Cotización #204 aceptada")
                .mensaje("La cotización fue aceptada")
                .leida(false)
                .creadoEn(Instant.now())
                .build();

        notificationWebSocketHandler.sendNotificationToUser(99L, notif);

        // 4. Verificar recepción en vivo en el formato acordado
        String livePayload = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(livePayload).isNotNull();
        JsonNode liveJson = objectMapper.readTree(livePayload);

        assertThat(liveJson.path("type").asText()).isEqualTo("notification.created");
        JsonNode data = liveJson.path("data");
        assertThat(data.path("id").asLong()).isEqualTo(204L);
        assertThat(data.path("sourceEventId").asText()).isEqualTo("evt-ws-live-101");
        assertThat(data.path("read").asBoolean()).isFalse();
        assertThat(data.path("titulo").asText()).isEqualTo("Cotización #204 aceptada");

        session.close();
    }

    @Test
    @DisplayName("Handshake con frame inválido es cerrado inmediatamente con código 1008")
    void standardWebSocketClient_invalidFirstFrame_closedWith1008() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<CloseStatus> closeStatuses = new LinkedBlockingQueue<>();

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeStatuses.offer(status);
            }
        };

        String wsUri = "ws://localhost:" + port + "/ws/notifications";
        WebSocketSession session = client.execute(clientHandler, wsUri).get(5, TimeUnit.SECONDS);
        assertThat(session.isOpen()).isTrue();

        // Enviar primer frame inválido (sin authenticate)
        session.sendMessage(new TextMessage("{\"type\":\"unauthorized_chat\"}"));

        CloseStatus status = closeStatuses.poll(5, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(status.getCode()).isEqualTo(1008);
    }

    @Test
    @DisplayName("Handshake sin autenticación dentro del timeout (2s) es cerrado con código 1008")
    void standardWebSocketClient_authTimeout_closedWith1008() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<CloseStatus> closeStatuses = new LinkedBlockingQueue<>();

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeStatuses.offer(status);
            }
        };

        String wsUri = "ws://localhost:" + port + "/ws/notifications";
        WebSocketSession session = client.execute(clientHandler, wsUri).get(5, TimeUnit.SECONDS);
        assertThat(session.isOpen()).isTrue();

        // No enviar frame de autenticación y esperar a que el servidor aplique el timeout (configurado a 4s en este test)
        CloseStatus status = closeStatuses.poll(6, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(status.getCode()).isEqualTo(1008);
    }
}
