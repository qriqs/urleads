package com.urleads.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationWebSocketHandlerTest {

    private JwtService jwtService;
    private UsuarioRepository usuarioRepository;
    private ObjectMapper objectMapper;
    private NotificationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Inicializar con timeout de 5 segundos
        handler = new NotificationWebSocketHandler(jwtService, usuarioRepository, objectMapper, 5L);
    }

    @Test
    @DisplayName("Conexión inicial se registra como no autenticada y programa tarea de timeout")
    void afterConnectionEstablished_InitializesUnauthenticatedWithTimeout() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        assertThat(attributes.get(NotificationWebSocketHandler.AUTHENTICATED_ATTR)).isEqualTo(false);
        assertThat(attributes.get(NotificationWebSocketHandler.TIMEOUT_TASK_ATTR)).isInstanceOf(ScheduledFuture.class);
    }

    @Test
    @DisplayName("Primer frame sin autenticación cierra la sesión con código 1008")
    void firstFrameWithoutAuth_ClosesSessionWith1008() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationWebSocketHandler.AUTHENTICATED_ATTR, false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        TextMessage invalidFrame = new TextMessage("{\"type\":\"PING\"}");
        handler.handleTextMessage(session, invalidFrame);

        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(1008);
    }

    @Test
    @DisplayName("Primer frame con { type: 'authenticate', accessToken: '...' } autentica con éxito")
    void firstFrameWithAuthenticateAndAccessToken_AuthenticatesSuccessfully() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationWebSocketHandler.AUTHENTICATED_ATTR, false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        String token = "valid.jwt.token";
        when(jwtService.extractSubject(token)).thenReturn("42");

        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setUsername("alex");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));

        TextMessage authFrame = new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"" + token + "\"}");
        handler.handleTextMessage(session, authFrame);

        assertThat(attributes.get(NotificationWebSocketHandler.AUTHENTICATED_ATTR)).isEqualTo(true);
        assertThat(attributes.get(NotificationWebSocketHandler.USER_ID_ATTR)).isEqualTo(42L);
        assertThat(handler.getActiveSessionsCount(42L)).isEqualTo(1);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        JsonNode responseJson = objectMapper.readTree(messageCaptor.getValue().getPayload());
        assertThat(responseJson.path("type").asText()).isEqualTo("authenticated");
        assertThat(responseJson.path("userId").asLong()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Token JWT inválido o expirado cierra la sesión con código 1008")
    void invalidToken_ClosesSessionWith1008() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationWebSocketHandler.AUTHENTICATED_ATTR, false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        when(jwtService.extractSubject("invalid-token")).thenThrow(new RuntimeException("Expired JWT"));

        TextMessage authFrame = new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"invalid-token\"}");
        handler.handleTextMessage(session, authFrame);

        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(1008);
    }

    @Test
    @DisplayName("Emisión de notificación envía { type: 'notification.created', data: { id, sourceEventId, createdAt, read: false } }")
    void sendNotificationToUser_SendsNotificationCreatedEnvelope() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationWebSocketHandler.AUTHENTICATED_ATTR, false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        when(jwtService.extractSubject("token123")).thenReturn("alex");
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setUsername("alex");
        when(usuarioRepository.findByUsername("alex")).thenReturn(Optional.of(usuario));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"authenticate\",\"accessToken\":\"token123\"}"));

        NotificationResponse notification = NotificationResponse.builder()
                .id(105L)
                .eventId("a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d")
                .tipo("COTIZACION_ACEPTADA")
                .titulo("Cotización #204 aceptada")
                .mensaje("La cotización #204 fue aceptada por USD 1250.00")
                .leida(false)
                .creadoEn(Instant.parse("2026-10-17T23:30:00Z"))
                .build();

        handler.sendNotificationToUser(42L, notification);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messageCaptor.capture());

        // El segundo mensaje es la notificación emitida
        String payload = messageCaptor.getAllValues().get(1).getPayload();
        JsonNode node = objectMapper.readTree(payload);

        assertThat(node.path("type").asText()).isEqualTo("notification.created");
        JsonNode data = node.path("data");
        assertThat(data.path("id").asLong()).isEqualTo(105L);
        assertThat(data.path("sourceEventId").asText()).isEqualTo("a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d");
        assertThat(data.path("createdAt").asText()).isEqualTo("2026-10-17T23:30:00Z");
        assertThat(data.path("read").asBoolean()).isFalse();
        assertThat(data.path("titulo").asText()).isEqualTo("Cotización #204 aceptada");
    }

    @Test
    @DisplayName("Timeout de autenticación (1s en test): sesión sin autenticar se cierra con 1008")
    void authenticationTimeout_ClosesSessionWith1008() throws Exception {
        NotificationWebSocketHandler timeoutHandler = new NotificationWebSocketHandler(jwtService, usuarioRepository, objectMapper, 1L);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        timeoutHandler.afterConnectionEstablished(session);

        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session, org.mockito.Mockito.timeout(3000)).close(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(1008);

        timeoutHandler.destroy();
    }
}
