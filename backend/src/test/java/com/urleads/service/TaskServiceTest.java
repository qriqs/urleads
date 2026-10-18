package com.urleads.service;

import com.urleads.dto.CreateTaskRequest;
import com.urleads.dto.LeadInternalResponse;
import com.urleads.dto.TaskResponse;
import com.urleads.dto.UpdateTaskRequest;
import com.urleads.entity.AgendaTarea;
import com.urleads.entity.Usuario;
import com.urleads.exception.InvalidLeadAssociationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.AgendaTareaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private AgendaTareaRepository agendaTareaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private LeadValidationService leadValidationService;

    private TaskService taskService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(agendaTareaRepository, usuarioRepository, leadValidationService);
        usuario = new Usuario(42L, "alex", "hash");
    }

    @Test
    @DisplayName("Crear tarea con lead válido: valida pertenencia y persiste con éxito")
    void crearTarea_conLeadValido_persisteExitosamente() {
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(leadValidationService.validarLeadPerteneceAUsuario(105L, "Bearer token"))
                .thenReturn(new LeadInternalResponse(105L, "EN_SEGUIMIENTO", LocalDate.now()));

        when(agendaTareaRepository.save(any(AgendaTarea.class))).thenAnswer(inv -> {
            AgendaTarea t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        CreateTaskRequest request = CreateTaskRequest.builder()
                .titulo("Llamar al cliente")
                .descripcion("Revisar detalles técnicos")
                .leadId(105L)
                .fechaLimite(LocalDate.of(2026, 10, 25))
                .build();

        TaskResponse response = taskService.crearTarea(42L, request, "Bearer token");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitulo()).isEqualTo("Llamar al cliente");
        assertThat(response.getLeadId()).isEqualTo(105L);
        assertThat(response.getEstado()).isEqualTo("PENDIENTE");

        verify(leadValidationService).validarLeadPerteneceAUsuario(105L, "Bearer token");
        verify(agendaTareaRepository).save(any(AgendaTarea.class));
    }

    @Test
    @DisplayName("Crear tarea con lead ajeno o inexistente: validación falla y rechaza vinculación")
    void crearTarea_conLeadAjeno_lanzaExcepcion() {
        doThrow(new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado"))
                .when(leadValidationService).validarLeadPerteneceAUsuario(999L, "Bearer token");

        CreateTaskRequest request = CreateTaskRequest.builder()
                .titulo("Tarea inválida")
                .leadId(999L)
                .build();

        assertThrows(
                InvalidLeadAssociationException.class,
                () -> taskService.crearTarea(42L, request, "Bearer token")
        );

        verify(agendaTareaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar tarea como completada: actualiza estado a COMPLETADA")
    void marcarComoCompletada_actualizaEstado() {
        AgendaTarea tarea = AgendaTarea.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Reunión")
                .estado("PENDIENTE")
                .build();

        when(agendaTareaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(tarea));
        when(agendaTareaRepository.save(any(AgendaTarea.class))).thenAnswer(i -> i.getArgument(0));

        TaskResponse response = taskService.marcarComoCompletada(10L, 42L);

        assertThat(response.getEstado()).isEqualTo("COMPLETADA");
        verify(agendaTareaRepository).save(tarea);
    }

    @Test
    @DisplayName("Aislamiento estricto: no permite ver ni modificar tareas de otro usuario")
    void aislamientoUsuario_tareaAjena_retornaNotFound() {
        when(agendaTareaRepository.findByIdAndUsuarioId(99L, 42L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.obtenerTareaPorId(99L, 42L)
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.marcarComoCompletada(99L, 42L)
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.eliminarTarea(99L, 42L)
        );
    }
}
