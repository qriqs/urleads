package com.urleads.websocket;

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
        handler = new NotificationWebSocketHandler(jwtService, usuarioRepository, objectMapper);
    }

    @Test
    @DisplayName("Conexión inicial se registra como no autenticada")
    void afterConnectionEstablished_InitializesUnauthenticated() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        assertThat(attributes.get("AUTHENTICATED")).isEqualTo(false);
    }

    @Test
    @DisplayName("Primer frame sin AUTH cierra la sesión por violación de política")
    void firstFrameWithoutAuth_ClosesSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("AUTHENTICATED", false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        TextMessage invalidFrame = new TextMessage("{\"type\":\"PING\"}");
        handler.handleTextMessage(session, invalidFrame);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("Primer frame con token JWT válido autentica la sesión exitosamente")
    void firstFrameWithValidToken_AuthenticatesSuccessfully() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("AUTHENTICATED", false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        String token = "valid.jwt.token";
        when(jwtService.extractSubject(token)).thenReturn("alex");

        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setUsername("alex");
        when(usuarioRepository.findByUsername("alex")).thenReturn(Optional.of(usuario));

        TextMessage authFrame = new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}");
        handler.handleTextMessage(session, authFrame);

        assertThat(attributes.get("AUTHENTICATED")).isEqualTo(true);
        assertThat(attributes.get("USER_ID")).isEqualTo(42L);
        assertThat(handler.getActiveSessionsCount(42L)).isEqualTo(1);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).contains("AUTH_SUCCESS");
    }

    @Test
    @DisplayName("Emisión de notificación envía mensaje a sesiones activas del usuario")
    void sendNotificationToUser_SendsToActiveSessions() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("AUTHENTICATED", false);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        when(jwtService.extractSubject("token123")).thenReturn("alex");
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        when(usuarioRepository.findByUsername("alex")).thenReturn(Optional.of(usuario));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"AUTH\",\"token\":\"token123\"}"));

        NotificationResponse notification = NotificationResponse.builder()
                .id(1L)
                .tipo("COTIZACION_ACEPTADA")
                .titulo("Cotización Aceptada")
                .mensaje("Total USD 1250.00")
                .creadoEn(Instant.now())
                .build();

        handler.sendNotificationToUser(42L, notification);

        // Se envía AUTH_SUCCESS y luego NOTIFICATION
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messageCaptor.capture());

        TextMessage sent = messageCaptor.getAllValues().get(1);
        assertThat(sent.getPayload()).contains("NOTIFICATION");
        assertThat(sent.getPayload()).contains("Cotización Aceptada");
    }
}
