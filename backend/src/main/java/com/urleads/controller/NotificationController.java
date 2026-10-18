package com.urleads.controller;

import com.urleads.dto.CreateNotificationRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.SnoozeNotificationRequest;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(NotificationService notificationService, CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Consulta paginada y determinista de notificaciones para el usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int validatedSize = Math.min(Math.max(1, size), 100);
        int validatedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(PageResponse.from(notificationService.obtenerNotificaciones(usuarioId, pageable)));
    }

    /**
     * Retorna la cantidad de notificaciones pendientes/no leídas.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        long count = notificationService.contarNoLeidas(usuarioId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * Marca una notificación específica como leída.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(notificationService.marcarComoLeida(id, usuarioId));
    }

    /**
     * Marca todas las notificaciones pendientes del usuario como leídas.
     */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        int updated = notificationService.marcarTodasComoLeidas(usuarioId);
        return ResponseEntity.ok(Map.of(
                "updated", updated,
                "message", "Todas las notificaciones fueron marcadas como leídas"
        ));
    }

    /**
     * Pospone una notificación por N minutos.
     */
    @PostMapping("/{id}/snooze")
    public ResponseEntity<NotificationResponse> snooze(
            @PathVariable Long id,
            @Valid @RequestBody SnoozeNotificationRequest request
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(notificationService.posponerNotificacion(id, usuarioId, request.getMinutos()));
    }

    /**
     * Crea y persiste una nueva notificación para el usuario autenticado.
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        NotificationResponse created = notificationService.crearNotificacion(usuarioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
