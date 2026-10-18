package com.urleads.service;

import com.urleads.dto.CreateNotificationRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.entity.Notification;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UsuarioRepository usuarioRepository;
    private NotificationWebSocketHandler webSocketHandler;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        webSocketHandler = mock(NotificationWebSocketHandler.class);
        notificationService = new NotificationService(notificationRepository, usuarioRepository, webSocketHandler);
    }

    @Test
    @DisplayName("Listar sin filtro invoca findByUsuarioIdOrderByCreadoEnDescIdDesc garantizando aislamiento")
    void list_WithoutFilter_QueriesDeterministicAll() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByUsuarioIdOrderByCreadoEnDescIdDesc(userId, pageable))
                .thenReturn(Page.empty(pageable));

        Page<NotificationResponse> result = notificationService.obtenerNotificaciones(userId, null, pageable);

        assertThat(result).isNotNull();
        verify(notificationRepository).findByUsuarioIdOrderByCreadoEnDescIdDesc(userId, pageable);
        verify(notificationRepository, never()).findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Listar con read=false filtra estrictamente solo no leídas del usuario")
    void list_WithReadFalse_QueriesUnreadOnly() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(userId, false, pageable))
                .thenReturn(Page.empty(pageable));

        Page<NotificationResponse> result = notificationService.obtenerNotificaciones(userId, false, pageable);

        assertThat(result).isNotNull();
        verify(notificationRepository).findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(userId, false, pageable);
    }

    @Test
    @DisplayName("Listar con read=true filtra estrictamente solo leídas del usuario")
    void list_WithReadTrue_QueriesReadOnly() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(userId, true, pageable))
                .thenReturn(Page.empty(pageable));

        Page<NotificationResponse> result = notificationService.obtenerNotificaciones(userId, true, pageable);

        assertThat(result).isNotNull();
        verify(notificationRepository).findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(userId, true, pageable);
    }

    @Test
    @DisplayName("Marcar como leída una notificación de otro usuario lanza ResourceNotFoundException (aislamiento)")
    void markAsRead_BelongingToAnotherUser_ThrowsNotFound() {
        Long userId = 1L;
        Long otherUserNotificationId = 99L;

        when(notificationRepository.findByIdAndUsuarioId(otherUserNotificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.marcarComoLeida(otherUserNotificationId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no encontrada o no pertenece al usuario autenticado");
    }

    @Test
    @DisplayName("Marcar como leída actualiza el estado y la fecha de lectura")
    void markAsRead_Success() {
        Long userId = 1L;
        Long notificationId = 10L;

        Usuario usuario = new Usuario();
        usuario.setId(userId);

        Notification notification = Notification.builder()
                .id(notificationId)
                .usuario(usuario)
                .tipo("RECORDATORIO")
                .titulo("Título")
                .mensaje("Mensaje")
                .leida(false)
                .build();

        when(notificationRepository.findByIdAndUsuarioId(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.marcarComoLeida(notificationId, userId);

        assertThat(response.isLeida()).isTrue();
        assertThat(response.getLeidaEn()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("Posponer notificación de otro usuario lanza ResourceNotFoundException")
    void snooze_BelongingToAnotherUser_ThrowsNotFound() {
        Long userId = 1L;
        Long notificationId = 99L;

        when(notificationRepository.findByIdAndUsuarioId(notificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.posponerNotificacion(notificationId, userId, 30))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Deduplicación idempotente: si eventId ya existe no inserta duplicado")
    void createNotification_DuplicateEventId_ReturnsExisting() {
        Long userId = 1L;
        String eventId = "evt-duplicate-123";

        Usuario usuario = new Usuario();
        usuario.setId(userId);

        Notification existing = Notification.builder()
                .id(50L)
                .eventId(eventId)
                .usuario(usuario)
                .tipo("COTIZACION")
                .titulo("Existente")
                .mensaje("Msg")
                .build();

        when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .eventId(eventId)
                .tipo("COTIZACION")
                .titulo("Nuevo")
                .mensaje("Msg")
                .build();

        NotificationResponse response = notificationService.crearNotificacion(userId, request);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getTitulo()).isEqualTo("Existente");
        verify(notificationRepository, never()).save(any());
        verify(webSocketHandler, never()).sendNotificationToUser(any(), any());
    }
}
