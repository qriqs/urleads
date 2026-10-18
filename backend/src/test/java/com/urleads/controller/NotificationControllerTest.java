package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.CreateNotificationRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.SnoozeNotificationRequest;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private NotaRepository notaRepository;

    @MockBean
    private com.urleads.repository.LeadRepository leadRepository;

    @Test
    @DisplayName("Petición sin autenticación debe retornar 401 Unauthorized")
    void withoutAuth_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Listar notificaciones con formato paginado completo conforme a contrato (incluyendo 'last')")
    void listNotifications_Authenticated_ReturnsOkWithContract() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse responseDto = NotificationResponse.builder()
                .id(10L)
                .tipo("COTIZACION_ACEPTADA")
                .titulo("Cotización Aceptada")
                .mensaje("El cliente aceptó la cotización")
                .leida(false)
                .creadoEn(Instant.now())
                .build();

        when(notificationService.obtenerNotificaciones(eq(1L), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].tipo").value("COTIZACION_ACEPTADA"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Listar notificaciones con filtro read=false retorna solo no leídas")
    void listNotifications_FilterReadFalse_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        when(notificationService.obtenerNotificaciones(eq(1L), eq(false), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/notifications?read=false&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Conteo de no leídas retorna número correcto")
    void unreadCount_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(notificationService.contarNoLeidas(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Marcar notificación como leída retorna 200 OK")
    void markAsRead_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse updated = NotificationResponse.builder()
                .id(10L)
                .leida(true)
                .leidaEn(Instant.now())
                .build();

        when(notificationService.marcarComoLeida(10L, 1L)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.leida").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Marcar notificación de otro usuario retorna 404 Not Found (aislamiento)")
    void markAsRead_BelongsToAnotherUser_ReturnsNotFound() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        when(notificationService.marcarComoLeida(99L, 1L))
                .thenThrow(new ResourceNotFoundException("Notificación no encontrada o no pertenece al usuario autenticado"));

        mockMvc.perform(patch("/api/v1/notifications/99/read"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Posponer notificación retorna 200 OK")
    void snoozeNotification_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse snoozed = NotificationResponse.builder()
                .id(10L)
                .pospuestaHasta(Instant.now().plusSeconds(1800))
                .build();

        when(notificationService.posponerNotificacion(eq(10L), eq(1L), any(SnoozeNotificationRequest.class))).thenReturn(snoozed);

        SnoozeNotificationRequest request = SnoozeNotificationRequest.builder()
                .minutos(30)
                .build();

        mockMvc.perform(post("/api/v1/notifications/10/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.pospuestaHasta").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Posponer notificación con preset '15m' retorna 200 OK")
    void snoozeNotification_WithPreset15m_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse snoozed = NotificationResponse.builder()
                .id(10L)
                .pospuestaHasta(Instant.now().plusSeconds(900))
                .snoozeCount(1)
                .build();

        when(notificationService.posponerNotificacion(eq(10L), eq(1L), any(SnoozeNotificationRequest.class))).thenReturn(snoozed);

        SnoozeNotificationRequest request = SnoozeNotificationRequest.builder()
                .preset("15m")
                .build();

        mockMvc.perform(post("/api/v1/notifications/10/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.snoozeCount").value(1));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Posponer notificación superando límite máximo retorna 400 Bad Request")
    void snoozeNotification_ExceedingLimit_ReturnsBadRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        when(notificationService.posponerNotificacion(eq(10L), eq(1L), any(SnoozeNotificationRequest.class)))
                .thenThrow(new IllegalArgumentException("Límite máximo de aplazamientos alcanzado (máximo permitido: 3)"));

        SnoozeNotificationRequest request = SnoozeNotificationRequest.builder()
                .preset("1h")
                .build();

        mockMvc.perform(post("/api/v1/notifications/10/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Límite máximo de aplazamientos alcanzado (máximo permitido: 3)"));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("Crear notificación persiste y retorna 201 Created")
    void createNotification_ReturnsCreated() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .eventId("evt-12345")
                .tipo("RECORDATORIO")
                .titulo("Llamar al cliente")
                .mensaje("Seguimiento programado")
                .build();

        NotificationResponse created = NotificationResponse.builder()
                .id(99L)
                .eventId("evt-12345")
                .tipo("RECORDATORIO")
                .titulo("Llamar al cliente")
                .mensaje("Seguimiento programado")
                .build();

        when(notificationService.crearNotificacion(eq(1L), any(CreateNotificationRequest.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.eventId").value("evt-12345"));
    }

    @Test
    @DisplayName("Autenticación con Bearer JWT usando claim sub como cadena decimal de usuario.id")
    void authWithJwtBearerToken_DecimalUserIdSub_AllowsAccess() throws Exception {
        // sub="42" (cadena decimal de usuario.id)
        String token = jwtService.generateToken("42", 60);

        Usuario user = new Usuario();
        user.setId(42L);
        user.setUsername("usuario42");
        user.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(user));
        when(usuarioRepository.findByUsername("usuario42")).thenReturn(Optional.of(user));
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        when(notificationService.contarNoLeidas(42L)).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
