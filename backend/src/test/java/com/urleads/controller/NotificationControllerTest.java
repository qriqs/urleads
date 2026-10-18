package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.CreateNotificationRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.SnoozeNotificationRequest;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("Listar notificaciones paginadas con usuario autenticado retorna 200 OK")
    void listNotifications_Authenticated_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse responseDto = NotificationResponse.builder()
                .id(10L)
                .tipo("COTIZACION_ACEPTADA")
                .titulo("Cotización Aceptada")
                .mensaje("El cliente aceptó la cotización")
                .leida(false)
                .creadoEn(Instant.now())
                .build();

        when(notificationService.obtenerNotificaciones(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].tipo").value("COTIZACION_ACEPTADA"))
                .andExpect(jsonPath("$.totalElements").value(1));
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
    @DisplayName("Posponer notificación retorna 200 OK")
    void snoozeNotification_ReturnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        NotificationResponse snoozed = NotificationResponse.builder()
                .id(10L)
                .pospuestaHasta(Instant.now().plusSeconds(1800))
                .build();

        when(notificationService.posponerNotificacion(10L, 1L, 30)).thenReturn(snoozed);

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
}
