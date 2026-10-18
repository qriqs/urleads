package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.CreateTaskRequest;
import com.urleads.dto.TaskResponse;
import com.urleads.dto.UpdateTaskRequest;
import com.urleads.entity.Usuario;
import com.urleads.exception.InvalidLeadAssociationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.TaskService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TaskService taskService;

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
    @DisplayName("POST /api/v1/tasks crea tarea exitosamente y retorna 201 Created")
    void createTask_returns201() throws Exception {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .titulo("Preparar propuesta comercial")
                .leadId(105L)
                .fechaLimite(LocalDate.of(2026, 10, 28))
                .build();

        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .leadId(105L)
                .titulo("Preparar propuesta comercial")
                .estado("PENDIENTE")
                .creadoEn(Instant.now())
                .build();

        when(taskService.crearTarea(eq(42L), any(CreateTaskRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Preparar propuesta comercial"))
                .andExpect(jsonPath("$.leadId").value(105L))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/tasks con lead ajeno retorna 400 Bad Request")
    void createTask_withForeignLead_returns400() throws Exception {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .titulo("Tarea con lead ajeno")
                .leadId(999L)
                .build();

        when(taskService.crearTarea(eq(42L), any(CreateTaskRequest.class), any()))
                .thenThrow(new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado"));

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/tasks retorna lista paginada conforme al contrato común")
    void listTasks_returnsPageResponse() throws Exception {
        TaskResponse t1 = TaskResponse.builder().id(1L).titulo("Tarea 1").estado("PENDIENTE").build();
        when(taskService.obtenerTareas(eq(42L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(t1)));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].titulo").value("Tarea 1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/tasks/{id}/complete marca tarea como completada")
    void completeTask_returns200WithCompletedState() throws Exception {
        TaskResponse completed = TaskResponse.builder().id(1L).titulo("Tarea 1").estado("COMPLETADA").build();
        when(taskService.marcarComoCompletada(1L, 42L)).thenReturn(completed);

        mockMvc.perform(patch("/api/v1/tasks/1/complete")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks/{id} retorna 204 No Content")
    void deleteTask_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/1")
                        .header("Authorization", validToken))
                .andExpect(status().isNoContent());
    }
}
