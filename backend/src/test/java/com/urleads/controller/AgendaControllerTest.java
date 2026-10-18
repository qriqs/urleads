package com.urleads.controller;

import com.urleads.dto.AgendaItemResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.AgendaQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AgendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AgendaQueryService agendaQueryService;

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
    @DisplayName("GET /api/v1/agenda retorna 200 OK con lista cronológica combinada y paginada")
    void getAgenda_validRange_returns200WithCombinedItems() throws Exception {
        AgendaItemResponse citaItem = AgendaItemResponse.builder()
                .tipo("CITA")
                .id(10L)
                .referencia("CITA-10")
                .titulo("Demostración Comercial")
                .fecha(LocalDate.of(2026, 10, 25))
                .hora(LocalTime.of(10, 0))
                .momentoUtc(Instant.parse("2026-10-25T15:00:00Z"))
                .finUtc(Instant.parse("2026-10-25T16:00:00Z"))
                .estado("PROGRAMADA")
                .build();

        AgendaItemResponse tareaItem = AgendaItemResponse.builder()
                .tipo("TAREA")
                .id(1L)
                .referencia("TAREA-1")
                .titulo("Enviar cotización revisada")
                .fecha(LocalDate.of(2026, 10, 25))
                .hora(LocalTime.of(14, 0))
                .momentoUtc(Instant.parse("2026-10-25T19:00:00Z"))
                .estado("PENDIENTE")
                .build();

        when(agendaQueryService.obtenerAgendaCombinada(eq(42L), eq("2026-10-25"), eq("2026-10-30"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(citaItem, tareaItem)));

        mockMvc.perform(get("/api/v1/agenda")
                        .header("Authorization", validToken)
                        .param("desde", "2026-10-25")
                        .param("hasta", "2026-10-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].tipo").value("CITA"))
                .andExpect(jsonPath("$.content[0].id").value(10L))
                .andExpect(jsonPath("$.content[0].referencia").value("CITA-10"))
                .andExpect(jsonPath("$.content[0].titulo").value("Demostración Comercial"))
                .andExpect(jsonPath("$.content[1].tipo").value("TAREA"))
                .andExpect(jsonPath("$.content[1].id").value(1L))
                .andExpect(jsonPath("$.content[1].referencia").value("TAREA-1"))
                .andExpect(jsonPath("$.content[1].titulo").value("Enviar cotización revisada"));
    }

    @Test
    @DisplayName("GET /api/v1/agenda con rango mayor a 31 días retorna 400 Bad Request")
    void getAgenda_rangeExceeding31Days_returns400() throws Exception {
        when(agendaQueryService.obtenerAgendaCombinada(eq(42L), eq("2026-10-01"), eq("2026-11-15"), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El rango de consulta no puede superar los 31 días (máximo permitido: 31 días)"));

        mockMvc.perform(get("/api/v1/agenda")
                        .header("Authorization", validToken)
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El rango de consulta no puede superar los 31 días (máximo permitido: 31 días)"));
    }

    @Test
    @DisplayName("GET /api/v1/agenda con fecha hasta anterior a desde retorna 400 Bad Request")
    void getAgenda_hastaBeforeDesde_returns400() throws Exception {
        when(agendaQueryService.obtenerAgendaCombinada(eq(42L), eq("2026-10-20"), eq("2026-10-10"), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a la fecha 'desde'"));

        mockMvc.perform(get("/api/v1/agenda")
                        .header("Authorization", validToken)
                        .param("desde", "2026-10-20")
                        .param("hasta", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha 'hasta' no puede ser anterior a la fecha 'desde'"));
    }

    @Test
    @DisplayName("GET /api/v1/agenda sin token de autenticación retorna 401 Unauthorized")
    void getAgenda_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/agenda")
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/agenda/daily-pending retorna 200 OK con resumen diario de tareas y citas")
    void getDailyPending_authenticated_returns200WithSummary() throws Exception {
        com.urleads.dto.DailyPendingResponse response = com.urleads.dto.DailyPendingResponse.builder()
                .fecha(LocalDate.of(2026, 10, 25))
                .zonaHoraria("America/Lima")
                .totalPendientes(2)
                .totalTareasPendientes(1)
                .totalCitasProgramadas(1)
                .tareas(List.of(com.urleads.dto.TaskResponse.builder()
                        .id(1L)
                        .titulo("Tarea de prueba")
                        .estado("PENDIENTE")
                        .fechaLimite(LocalDate.of(2026, 10, 25))
                        .build()))
                .citas(List.of(com.urleads.dto.AppointmentResponse.builder()
                        .id(10L)
                        .titulo("Cita de prueba")
                        .estado("PROGRAMADA")
                        .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                        .fin(Instant.parse("2026-10-25T16:00:00Z"))
                        .build()))
                .items(List.of(
                        AgendaItemResponse.builder()
                                .tipo("CITA")
                                .id(10L)
                                .titulo("Cita de prueba")
                                .build(),
                        AgendaItemResponse.builder()
                                .tipo("TAREA")
                                .id(1L)
                                .titulo("Tarea de prueba")
                                .build()
                ))
                .build();

        when(agendaQueryService.obtenerResumenPendientesDelDia(eq(42L), eq("2026-10-25"), eq("America/Lima")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/agenda/daily-pending")
                        .header("Authorization", validToken)
                        .param("fecha", "2026-10-25")
                        .param("zonaHoraria", "America/Lima"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-10-25"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.totalPendientes").value(2))
                .andExpect(jsonPath("$.totalTareasPendientes").value(1))
                .andExpect(jsonPath("$.totalCitasProgramadas").value(1))
                .andExpect(jsonPath("$.tareas").isArray())
                .andExpect(jsonPath("$.tareas[0].id").value(1L))
                .andExpect(jsonPath("$.citas").isArray())
                .andExpect(jsonPath("$.citas[0].id").value(10L))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].tipo").value("CITA"))
                .andExpect(jsonPath("$.items[1].tipo").value("TAREA"));
    }

    @Test
    @DisplayName("GET /api/v1/agenda/daily-pending sin token de autenticación retorna 401 Unauthorized")
    void getDailyPending_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/agenda/daily-pending"))
                .andExpect(status().isUnauthorized());
    }
}
