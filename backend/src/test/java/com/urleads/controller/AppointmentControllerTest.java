package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.*;
import com.urleads.entity.Usuario;
import com.urleads.exception.InvalidLeadAssociationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.AppointmentService;
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
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AppointmentService appointmentService;

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
    @DisplayName("POST /api/v1/appointments crea cita exitosamente y retorna 201 Created")
    void createAppointment_returns201() throws Exception {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .titulo("Demostración de CRM UrLeads")
                .descripcion("Revisión de módulos con el cliente")
                .leadId(105L)
                .inicio("2026-10-25T10:00:00-05:00")
                .fin("2026-10-25T11:00:00-05:00")
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build();

        AppointmentResponse response = AppointmentResponse.builder()
                .id(1L)
                .usuarioId(42L)
                .leadId(105L)
                .titulo("Demostración de CRM UrLeads")
                .descripcion("Revisión de módulos con el cliente")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .zonaHoraria("America/Lima")
                .inicioLocal("2026-10-25T10:00:00-05:00")
                .finLocal("2026-10-25T11:00:00-05:00")
                .estado("PROGRAMADA")
                .creadoEn(Instant.now())
                .build();

        when(appointmentService.crearCita(eq(42L), any(CreateAppointmentRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Demostración de CRM UrLeads"))
                .andExpect(jsonPath("$.leadId").value(105L))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.inicio").value("2026-10-25T15:00:00Z"))
                .andExpect(jsonPath("$.fin").value("2026-10-25T16:00:00Z"))
                .andExpect(jsonPath("$.inicioLocal").value("2026-10-25T10:00:00-05:00"))
                .andExpect(jsonPath("$.finLocal").value("2026-10-25T11:00:00-05:00"));
    }

    @Test
    @DisplayName("POST /api/v1/appointments con fin anterior a inicio retorna 400 Bad Request")
    void createAppointment_invalidSchedule_returns400() throws Exception {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .titulo("Cita con horario inconsistente")
                .inicio("2026-10-25T16:00:00Z")
                .fin("2026-10-25T15:00:00Z")
                .build();

        when(appointmentService.crearCita(eq(42L), any(CreateAppointmentRequest.class), any()))
                .thenThrow(new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio"));

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha de fin debe ser posterior a la de inicio"));
    }

    @Test
    @DisplayName("POST /api/v1/appointments con lead ajeno retorna 400 Bad Request")
    void createAppointment_foreignLead_returns400() throws Exception {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .titulo("Cita con lead ajeno")
                .leadId(999L)
                .inicio("2026-10-25T10:00:00-05:00")
                .fin("2026-10-25T11:00:00-05:00")
                .build();

        when(appointmentService.crearCita(eq(42L), any(CreateAppointmentRequest.class), any()))
                .thenThrow(new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado"));

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/appointments retorna lista paginada conforme al contrato común")
    void listAppointments_returnsPageResponse() throws Exception {
        AppointmentResponse c1 = AppointmentResponse.builder()
                .id(1L)
                .titulo("Cita 1")
                .estado("PROGRAMADA")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .build();

        when(appointmentService.obtenerCitas(eq(42L), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(c1)));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].titulo").value("Cita 1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/{id} retorna 200 OK con los datos de la cita")
    void getAppointmentById_returns200() throws Exception {
        AppointmentResponse c1 = AppointmentResponse.builder()
                .id(1L)
                .titulo("Cita Encontrada")
                .estado("PROGRAMADA")
                .build();

        when(appointmentService.obtenerCitaPorId(1L, 42L)).thenReturn(c1);

        mockMvc.perform(get("/api/v1/appointments/1")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Cita Encontrada"));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/{id} para cita ajena retorna 404 Not Found")
    void getAppointmentById_foreign_returns404() throws Exception {
        when(appointmentService.obtenerCitaPorId(99L, 42L))
                .thenThrow(new ResourceNotFoundException("Cita no encontrada con ID: 99"));

        mockMvc.perform(get("/api/v1/appointments/99")
                        .header("Authorization", validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/appointments/{id} actualiza cita y retorna 200 OK")
    void updateAppointment_returns200() throws Exception {
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .titulo("Título actualizado")
                .inicio("2026-10-25T11:00:00-05:00")
                .fin("2026-10-25T12:00:00-05:00")
                .estado("PROGRAMADA")
                .build();

        AppointmentResponse response = AppointmentResponse.builder()
                .id(1L)
                .titulo("Título actualizado")
                .estado("PROGRAMADA")
                .build();

        when(appointmentService.actualizarCita(eq(1L), eq(42L), any(UpdateAppointmentRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/appointments/1")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título actualizado"));
    }

    @Test
    @DisplayName("PATCH /api/v1/appointments/{id}/status actualiza el estado a REALIZADA")
    void updateStatus_returns200WithNewState() throws Exception {
        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest("REALIZADA");

        AppointmentResponse response = AppointmentResponse.builder()
                .id(1L)
                .titulo("Cita 1")
                .estado("REALIZADA")
                .build();

        when(appointmentService.actualizarEstadoCita(1L, 42L, "REALIZADA")).thenReturn(response);

        mockMvc.perform(patch("/api/v1/appointments/1/status")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REALIZADA"));
    }

    @Test
    @DisplayName("PATCH /api/v1/appointments/{id}/status con estado inválido retorna 400 Bad Request")
    void updateStatus_invalidState_returns400() throws Exception {
        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest("DESCONOCIDO");

        when(appointmentService.actualizarEstadoCita(1L, 42L, "DESCONOCIDO"))
                .thenThrow(new IllegalArgumentException("Estado de cita inválido: 'DESCONOCIDO'. Estados permitidos: PROGRAMADA, REALIZADA, CANCELADA"));

        mockMvc.perform(patch("/api/v1/appointments/1/status")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Estado de cita inválido: 'DESCONOCIDO'. Estados permitidos: PROGRAMADA, REALIZADA, CANCELADA"));
    }

    @Test
    @DisplayName("DELETE /api/v1/appointments/{id} retorna 204 No Content")
    void deleteAppointment_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/1")
                        .header("Authorization", validToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/appointments/{id}/outcome registra minuta y resultado retornando 200 OK")
    void recordOutcome_authenticated_returns200WithOutcome() throws Exception {
        AppointmentOutcomeRequest request = AppointmentOutcomeRequest.builder()
                .resumen("Demostración completada con éxito.")
                .acuerdos("Enviar cotización ajustada.")
                .proximasAcciones("Coordinar firma de contrato.")
                .resultado("REALIZADA")
                .asistencia("ASISTIERON_TODOS")
                .build();

        AppointmentOutcomeResponse outcomeResponse = AppointmentOutcomeResponse.builder()
                .id(50L)
                .citaId(1L)
                .usuarioId(42L)
                .resumen("Demostración completada con éxito.")
                .minuta("Demostración completada con éxito.")
                .acuerdos("Enviar cotización ajustada.")
                .proximasAcciones("Coordinar firma de contrato.")
                .resultado("REALIZADA")
                .asistencia("ASISTIERON_TODOS")
                .estadoCita("REALIZADA")
                .creadoEn(Instant.parse("2026-10-25T16:00:00Z"))
                .actualizadoEn(Instant.parse("2026-10-25T16:00:00Z"))
                .build();

        when(appointmentService.registrarResultadoCita(eq(1L), eq(42L), any(AppointmentOutcomeRequest.class)))
                .thenReturn(outcomeResponse);

        mockMvc.perform(post("/api/v1/appointments/1/outcome")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50L))
                .andExpect(jsonPath("$.citaId").value(1L))
                .andExpect(jsonPath("$.usuarioId").value(42L))
                .andExpect(jsonPath("$.resumen").value("Demostración completada con éxito."))
                .andExpect(jsonPath("$.minuta").value("Demostración completada con éxito."))
                .andExpect(jsonPath("$.acuerdos").value("Enviar cotización ajustada."))
                .andExpect(jsonPath("$.proximasAcciones").value("Coordinar firma de contrato."))
                .andExpect(jsonPath("$.resultado").value("REALIZADA"))
                .andExpect(jsonPath("$.estadoCita").value("REALIZADA"));

        // Confirmar que no hubo interacción con notas de CRM
        org.mockito.Mockito.verifyNoInteractions(notaRepository);
    }

    @Test
    @DisplayName("POST /api/v1/appointments/{id}/outcome sin autenticación retorna 401 Unauthorized")
    void recordOutcome_unauthenticated_returns401() throws Exception {
        AppointmentOutcomeRequest request = AppointmentOutcomeRequest.builder()
                .resumen("Sin token")
                .build();

        mockMvc.perform(post("/api/v1/appointments/1/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/appointments/{id}/outcome retorna 200 OK con minuta existente")
    void getOutcome_authenticated_returns200() throws Exception {
        AppointmentOutcomeResponse outcomeResponse = AppointmentOutcomeResponse.builder()
                .id(50L)
                .citaId(1L)
                .usuarioId(42L)
                .resumen("Minuta guardada.")
                .resultado("REALIZADA")
                .build();

        when(appointmentService.obtenerResultadoCita(1L, 42L)).thenReturn(outcomeResponse);

        mockMvc.perform(get("/api/v1/appointments/1/outcome")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50L))
                .andExpect(jsonPath("$.resumen").value("Minuta guardada."));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/{id}/outcome cuando no existe retorna 404 Not Found")
    void getOutcome_notFound_returns404() throws Exception {
        when(appointmentService.obtenerResultadoCita(99L, 42L))
                .thenThrow(new ResourceNotFoundException("No se ha registrado resultado o minuta para la cita ID: 99"));

        mockMvc.perform(get("/api/v1/appointments/99/outcome")
                        .header("Authorization", validToken))
                .andExpect(status().isNotFound());
    }
}
