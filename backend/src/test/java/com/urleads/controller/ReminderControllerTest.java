package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.CreateReminderRequest;
import com.urleads.dto.ReminderResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReminderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ReminderService reminderService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private NotaRepository notaRepository;

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
    private LeadRepository leadRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario(42L, "alex", "hash");
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("42")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("alex")).thenReturn(Optional.of(usuario));

        validToken = "Bearer " + jwtService.generateToken("42");
    }

    @Test
    @DisplayName("POST /api/v1/reminders programa recordatorio y retorna 201 Created")
    void createReminder_returns201() throws Exception {
        CreateReminderRequest request = CreateReminderRequest.builder()
                .targetType("CITA")
                .targetId(10L)
                .programadoPara("2026-10-25T15:00:00Z")
                .antelacionMinutos(15)
                .build();

        ReminderResponse response = ReminderResponse.builder()
                .id(1L)
                .occurrenceId("REM-CITA-10-15M")
                .usuarioId(42L)
                .targetType("CITA")
                .targetId(10L)
                .programadoPara(Instant.parse("2026-10-25T14:45:00Z"))
                .estado("PENDIENTE")
                .build();

        when(reminderService.programarRecordatorio(eq(42L), any(CreateReminderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.occurrenceId").value("REM-CITA-10-15M"))
                .andExpect(jsonPath("$.targetType").value("CITA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("GET /api/v1/reminders retorna lista paginada de recordatorios del usuario")
    void listReminders_returns200() throws Exception {
        ReminderResponse r1 = ReminderResponse.builder()
                .id(1L)
                .occurrenceId("REM-CITA-10-15M")
                .targetType("CITA")
                .targetId(10L)
                .estado("PENDIENTE")
                .build();

        when(reminderService.obtenerRecordatoriosUsuario(eq(42L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(r1)));

        mockMvc.perform(get("/api/v1/reminders")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].occurrenceId").value("REM-CITA-10-15M"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/reminders/{id} cancela recordatorio y retorna 204 No Content")
    void cancelReminder_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/reminders/1")
                        .header("Authorization", validToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/reminders/process ejecuta ciclo manual de despacho y retorna total procesado")
    void processPendingReminders_returns200() throws Exception {
        when(reminderService.procesarRecordatoriosPendientes(any())).thenReturn(3);

        mockMvc.perform(post("/api/v1/reminders/process")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesados").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/reminders sin autenticación retorna 401 Unauthorized")
    void listReminders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reminders"))
                .andExpect(status().isUnauthorized());
    }
}
