package com.urleads.service;

import com.urleads.dto.CreateNotificationRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Notification;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final int MAX_SNOOZE_COUNT = 3;

    private final NotificationRepository notificationRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationRepository notificationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UsuarioRepository usuarioRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationWebSocketHandler webSocketHandler
    ) {
        this.notificationRepository = notificationRepository;
        this.usuarioRepository = usuarioRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Persiste una notificación en PostgreSQL antes de emitirla en tiempo real por WebSocket.
     * Soporta deduplicación idempotente mediante eventId.
     */
    @Transactional
    public NotificationResponse crearNotificacion(Long usuarioId, CreateNotificationRequest request) {
        // Deduplicación idempotente
        if (request.getEventId() != null && !request.getEventId().isBlank()) {
            Optional<Notification> existente = notificationRepository.findByEventId(request.getEventId());
            if (existente.isPresent()) {
                log.info("Notificación con eventId {} ya existe. Retornando registro existente (idempotencia).", request.getEventId());
                return NotificationResponse.fromEntity(existente.get());
            }
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId));

        Notification notification = Notification.builder()
                .usuario(usuario)
                .eventId(request.getEventId())
                .tipo(request.getTipo())
                .titulo(request.getTitulo())
                .mensaje(request.getMensaje())
                .data(request.getData())
                .leida(false)
                .creadoEn(Instant.now())
                .build();

        Notification guardada = notificationRepository.save(notification);
        NotificationResponse response = NotificationResponse.fromEntity(guardada);

        // Emisión por WebSocket si hay conexión activa (si no, queda en BD para consulta REST)
        webSocketHandler.sendNotificationToUser(usuarioId, response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> obtenerNotificaciones(Long usuarioId, Boolean read, Pageable pageable) {
        if (notificationRepository == null) {
            return Page.empty(pageable);
        }
        Page<Notification> pagina = (read == null)
                ? notificationRepository.findByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId, pageable)
                : notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(usuarioId, read, pageable);
        return pagina.map(NotificationResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> obtenerNotificaciones(Long usuarioId, Pageable pageable) {
        return obtenerNotificaciones(usuarioId, null, pageable);
    }

    @Transactional
    public NotificationResponse marcarComoLeida(Long id, Long usuarioId) {
        Notification notification = notificationRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada o no pertenece al usuario autenticado"));

        if (!notification.isLeida()) {
            notification.setLeida(true);
            notification.setLeidaEn(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.fromEntity(notification);
    }

    @Transactional
    public int marcarTodasComoLeidas(Long usuarioId) {
        return notificationRepository.markAllAsRead(usuarioId, Instant.now());
    }

    @Transactional
    public NotificationResponse posponerNotificacion(Long id, Long usuarioId, com.urleads.dto.SnoozeNotificationRequest request) {
        Notification notification = notificationRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada o no pertenece al usuario autenticado"));

        // Validación estricta del límite máximo de aplazamientos
        if (notification.getSnoozeCount() >= MAX_SNOOZE_COUNT) {
            throw new IllegalArgumentException("Límite máximo de aplazamientos alcanzado (máximo permitido: " + MAX_SNOOZE_COUNT + ")");
        }

        int minutos = (request != null) ? request.resolverMinutos() : 15;
        Instant pospuesta = Instant.now().plus(minutos, ChronoUnit.MINUTES);

        notification.setPospuestaHasta(pospuesta);
        notification.setSnoozeCount(notification.getSnoozeCount() + 1);
        notification = notificationRepository.save(notification);

        return NotificationResponse.fromEntity(notification);
    }

    @Transactional
    public NotificationResponse posponerNotificacion(Long id, Long usuarioId, int minutos) {
        return posponerNotificacion(id, usuarioId, com.urleads.dto.SnoozeNotificationRequest.builder().minutos(minutos).build());
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(Long usuarioId) {
        return notificationRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }
}
