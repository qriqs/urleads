package com.urleads.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler de WebSocket estándar según RFC 6455 para notificaciones en vivo.
 * Implementa autenticación obligatoria en el primer frame sin tokens en la query string.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final String USER_ID_ATTR = "USER_ID";
    private static final String AUTHENTICATED_ATTR = "AUTHENTICATED";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    // Mapa thread-safe de usuarioId -> Conjunto de sesiones WebSocket activas
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(
            @org.springframework.beans.factory.annotation.Autowired(required = false) JwtService jwtService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UsuarioRepository usuarioRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(AUTHENTICATED_ATTR, false);
        log.info("Conexión WebSocket RFC 6455 establecida (sesión {}). Esperando frame de autenticación.", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Boolean isAuthenticated = (Boolean) session.getAttributes().getOrDefault(AUTHENTICATED_ATTR, false);

        if (!Boolean.TRUE.equals(isAuthenticated)) {
            // Primer frame: DEBE ser de tipo AUTH con el token JWT
            authenticateFirstFrame(session, message.getPayload());
            return;
        }

        // Si ya está autenticado, procesar mensajes de control (ej. PING)
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            if ("PING".equalsIgnoreCase(type)) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "PONG"))));
            }
        } catch (Exception e) {
            log.debug("Mensaje ignorado de sesión {}: {}", session.getId(), e.getMessage());
        }
    }

    private void authenticateFirstFrame(WebSocketSession session, String payload) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");
            String token = root.path("token").asText("");

            if (!"AUTH".equalsIgnoreCase(type) || token.isBlank()) {
                log.warn("Sesión {} no envió frame AUTH válido como primer frame. Cerrando conexión.", session.getId());
                sendErrorAndClose(session, CloseStatus.POLICY_VIOLATION, "El primer frame debe ser de tipo AUTH con token JWT");
                return;
            }

            // Validar token JWT
            String username = jwtService.extractSubject(token);
            Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(username);

            if (usuarioOpt.isEmpty()) {
                log.warn("Usuario {} del token no existe. Cerrando sesión WebSocket.", username);
                sendErrorAndClose(session, CloseStatus.NOT_ACCEPTABLE, "Usuario no encontrado");
                return;
            }

            Long userId = usuarioOpt.get().getId();
            session.getAttributes().put(USER_ID_ATTR, userId);
            session.getAttributes().put(AUTHENTICATED_ATTR, true);

            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);

            log.info("Sesión WebSocket {} autenticada con éxito para usuario {}", session.getId(), userId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "AUTH_SUCCESS",
                    "message", "Conexión WebSocket autenticada según RFC 6455"
            ))));

        } catch (Exception e) {
            log.warn("Error autenticando sesión WebSocket {}: {}", session.getId(), e.getMessage());
            sendErrorAndClose(session, CloseStatus.POLICY_VIOLATION, "Token JWT inválido o expirado");
        }
    }

    private void sendErrorAndClose(WebSocketSession session, CloseStatus status, String reason) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                        "type", "ERROR",
                        "message", reason
                ))));
                session.close(status);
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        log.info("Conexión WebSocket {} cerrada con estado: {}", session.getId(), status);
    }

    /**
     * Emite una notificación en tiempo real a todas las conexiones WebSocket activas del usuario.
     */
    public void sendNotificationToUser(Long usuarioId, NotificationResponse notification) {
        Set<WebSocketSession> sessions = userSessions.get(usuarioId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("Usuario {} sin conexiones WebSocket activas. La notificación quedó respaldada en PostgreSQL.", usuarioId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "NOTIFICATION",
                    "payload", notification
            ));
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Error enviando frame a sesión {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error serializando notificación para WebSocket: {}", e.getMessage(), e);
        }
    }

    public int getActiveSessionsCount(Long usuarioId) {
        Set<WebSocketSession> sessions = userSessions.get(usuarioId);
        return sessions != null ? sessions.size() : 0;
    }
}
