package com.urleads.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Servidor WebSocket estándar según RFC 6455 en /ws/notifications.
 * No utiliza Socket.IO ni STOMP/SockJS por defecto.
 * La conexión requiere autenticación en el primer frame dentro de 5s o se cierra con código 1008.
 * Nunca envía tokens en query string ni imprime tokens en logs.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    public static final String USER_ID_ATTR = "USER_ID";
    public static final String AUTHENTICATED_ATTR = "AUTHENTICATED";
    public static final String TIMEOUT_TASK_ATTR = "TIMEOUT_TASK";

    public static final CloseStatus POLICY_VIOLATION_1008 = new CloseStatus(1008, "Policy Violation");

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final long authTimeoutSeconds;

    // Mapa thread-safe de usuarioId -> Conjunto de sesiones WebSocket activas
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Autowired
    public NotificationWebSocketHandler(
            @Autowired(required = false) JwtService jwtService,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${websocket.auth.timeout-seconds:5}") long authTimeoutSeconds
    ) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.authTimeoutSeconds = authTimeoutSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-auth-timeout");
            t.setDaemon(true);
            return t;
        });
    }

    public NotificationWebSocketHandler(
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            ObjectMapper objectMapper
    ) {
        this(jwtService, usuarioRepository, objectMapper, 5L);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(AUTHENTICATED_ATTR, false);
        log.info("Conexión WebSocket RFC 6455 establecida (sesión {}). Esperando frame de autenticación dentro de {}s...",
                session.getId(), authTimeoutSeconds);

        // Programar cierre con código 1008 tras timeout de 5 segundos si no se autentica
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            try {
                Boolean auth = (Boolean) session.getAttributes().getOrDefault(AUTHENTICATED_ATTR, false);
                if (!Boolean.TRUE.equals(auth) && session.isOpen()) {
                    log.warn("Sesión {} superó el timeout de autenticación ({}s). Cerrando con código 1008.",
                            session.getId(), authTimeoutSeconds);
                    sendErrorAndClose(session, POLICY_VIOLATION_1008, "Authentication timeout (5s)");
                }
            } catch (Exception e) {
                log.debug("Error cerrando sesión expirada {}: {}", session.getId(), e.getMessage());
            }
        }, authTimeoutSeconds, TimeUnit.SECONDS);

        session.getAttributes().put(TIMEOUT_TASK_ATTR, timeoutTask);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Boolean isAuthenticated = (Boolean) session.getAttributes().getOrDefault(AUTHENTICATED_ATTR, false);

        if (!Boolean.TRUE.equals(isAuthenticated)) {
            // Primer frame: DEBE ser frame de autenticación
            authenticateFirstFrame(session, message.getPayload());
            return;
        }

        // Si ya está autenticado, procesar mensajes de control (ej. ping/pong)
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            if ("PING".equalsIgnoreCase(type) || "ping".equalsIgnoreCase(type)) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "pong"))));
            }
        } catch (Exception e) {
            log.debug("Mensaje ignorado de sesión {}: {}", session.getId(), e.getMessage());
        }
    }

    private void authenticateFirstFrame(WebSocketSession session, String payload) throws IOException {
        cancelTimeout(session);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");

            // Admite { type: "authenticate", accessToken: "..." } y alias { type: "AUTH", token: "..." }
            String token = root.hasNonNull("accessToken")
                    ? root.path("accessToken").asText("")
                    : root.path("token").asText("");

            boolean isAuthType = "authenticate".equalsIgnoreCase(type) || "AUTH".equalsIgnoreCase(type);

            if (!isAuthType || token.isBlank()) {
                log.warn("Sesión {} envió frame no autenticado como primer frame. Cerrando con código 1008.", session.getId());
                sendErrorAndClose(session, POLICY_VIOLATION_1008, "El primer frame debe ser { type: 'authenticate', accessToken: '...' }");
                return;
            }

            if (jwtService == null) {
                log.error("JwtService no disponible para validar sesión WebSocket.");
                sendErrorAndClose(session, POLICY_VIOLATION_1008, "Servicio de autenticación no disponible");
                return;
            }

            // Validar token sin imprimirlo jamás en logs ni query string
            String subject = jwtService.extractSubject(token);
            if (subject == null || subject.isBlank()) {
                log.warn("Sesión {} presentó token sin subject válido. Cerrando con código 1008.", session.getId());
                sendErrorAndClose(session, POLICY_VIOLATION_1008, "Token inválido o expirado");
                return;
            }

            Usuario usuario = resolverUsuarioPorSubject(subject);
            if (usuario == null) {
                log.warn("Usuario no encontrado para el token presentado. Cerrando con código 1008.");
                sendErrorAndClose(session, POLICY_VIOLATION_1008, "Usuario no encontrado");
                return;
            }

            Long userId = usuario.getId();
            session.getAttributes().put(USER_ID_ATTR, userId);
            session.getAttributes().put(AUTHENTICATED_ATTR, true);

            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);

            log.info("Sesión WebSocket {} autenticada con éxito para usuario {}", session.getId(), userId);

            // Frame de confirmación
            Map<String, Object> authResponse = new LinkedHashMap<>();
            authResponse.put("type", "authenticated");
            authResponse.put("userId", userId);
            authResponse.put("message", "Conexión WebSocket autenticada según RFC 6455");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(authResponse)));

        } catch (Exception e) {
            log.warn("Error autenticando sesión WebSocket {}: Cerrando con código 1008.", session.getId());
            sendErrorAndClose(session, POLICY_VIOLATION_1008, "Token JWT inválido o expirado");
        }
    }

    private void cancelTimeout(WebSocketSession session) {
        ScheduledFuture<?> future = (ScheduledFuture<?>) session.getAttributes().remove(TIMEOUT_TASK_ATTR);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void sendErrorAndClose(WebSocketSession session, CloseStatus status, String reason) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                        "type", "error",
                        "code", status.getCode(),
                        "message", reason
                ))));
                session.close(status);
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelTimeout(session);
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
     * Emite una notificación en tiempo real a todas las conexiones WebSocket activas del usuario autenticado.
     * Envía { type: "notification.created", data: { id, sourceEventId, createdAt, read: false, ... } }
     */
    public void sendNotificationToUser(Long usuarioId, NotificationResponse notification) {
        Set<WebSocketSession> sessions = userSessions.get(usuarioId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("Usuario {} sin conexiones WebSocket activas. La notificación quedó respaldada en PostgreSQL.", usuarioId);
            return;
        }

        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", notification.getId());
            data.put("sourceEventId", notification.getEventId());
            data.put("createdAt", notification.getCreadoEn() != null ? notification.getCreadoEn().toString() : null);
            data.put("read", notification.isLeida());
            data.put("tipo", notification.getTipo());
            data.put("titulo", notification.getTitulo());
            data.put("mensaje", notification.getMensaje());
            if (notification.getData() != null) {
                data.put("payloadData", notification.getData());
            }

            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("type", "notification.created");
            eventPayload.put("data", data);

            String json = objectMapper.writeValueAsString(eventPayload);
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

    private Usuario resolverUsuarioPorSubject(String subject) {
        if (usuarioRepository == null) {
            return null;
        }
        if (subject != null && subject.matches("\\d+")) {
            try {
                Long id = Long.parseLong(subject);
                var opt = usuarioRepository.findById(id);
                if (opt.isPresent()) {
                    return opt.get();
                }
            } catch (NumberFormatException ignored) {}
        }
        return usuarioRepository.findByUsername(subject).orElse(null);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}
