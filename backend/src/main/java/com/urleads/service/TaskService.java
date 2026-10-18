package com.urleads.service;

import com.urleads.dto.CreateTaskRequest;
import com.urleads.dto.TaskResponse;
import com.urleads.dto.UpdateTaskRequest;
import com.urleads.entity.AgendaTarea;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.AgendaTareaRepository;
import com.urleads.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
public class TaskService {

    private final AgendaTareaRepository agendaTareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LeadValidationService leadValidationService;

    public TaskService(
            @Autowired(required = false) AgendaTareaRepository agendaTareaRepository,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) LeadValidationService leadValidationService
    ) {
        this.agendaTareaRepository = agendaTareaRepository;
        this.usuarioRepository = usuarioRepository;
        this.leadValidationService = leadValidationService;
    }

    @Transactional
    public TaskResponse crearTarea(Long usuarioId, CreateTaskRequest request, String token) {
        // Validación obligatoria de pertenencia de lead si está presente
        if (request.getLeadId() != null && leadValidationService != null) {
            leadValidationService.validarLeadPerteneceAUsuario(request.getLeadId(), token);
        }

        Usuario usuario = usuarioRepository != null
                ? usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId))
                : null;

        String estadoInicial = (request.getEstado() != null && !request.getEstado().isBlank())
                ? request.getEstado().toUpperCase()
                : "PENDIENTE";

        AgendaTarea tarea = AgendaTarea.builder()
                .usuario(usuario)
                .leadId(request.getLeadId())
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .fechaLimite(request.getFechaLimite())
                .horaLimite(request.getHoraLimite())
                .estado(estadoInicial)
                .creadoEn(Instant.now())
                .actualizadoEn(Instant.now())
                .build();

        AgendaTarea guardada = agendaTareaRepository != null ? agendaTareaRepository.save(tarea) : tarea;
        return TaskResponse.fromEntity(guardada);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> obtenerTareas(Long usuarioId, Long leadId, String estado, Pageable pageable) {
        if (agendaTareaRepository == null) {
            return Page.empty(pageable);
        }

        String estadoNormalizado = (estado != null && !estado.isBlank()) ? estado.toUpperCase() : null;

        Page<AgendaTarea> pagina;
        if (leadId != null && estadoNormalizado != null) {
            pagina = agendaTareaRepository.findByUsuarioIdAndEstadoAndLeadIdOrderByCreadoEnDescIdDesc(
                    usuarioId, estadoNormalizado, leadId, pageable);
        } else if (leadId != null) {
            pagina = agendaTareaRepository.findByUsuarioIdAndLeadIdOrderByCreadoEnDescIdDesc(
                    usuarioId, leadId, pageable);
        } else if (estadoNormalizado != null) {
            pagina = agendaTareaRepository.findByUsuarioIdAndEstadoOrderByCreadoEnDescIdDesc(
                    usuarioId, estadoNormalizado, pageable);
        } else {
            pagina = agendaTareaRepository.findByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId, pageable);
        }

        return pagina.map(TaskResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public TaskResponse obtenerTareaPorId(Long id, Long usuarioId) {
        if (agendaTareaRepository == null) {
            throw new ResourceNotFoundException("Tarea no encontrada con ID: " + id);
        }
        AgendaTarea tarea = agendaTareaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con ID: " + id));
        return TaskResponse.fromEntity(tarea);
    }

    @Transactional
    public TaskResponse actualizarTarea(Long id, Long usuarioId, UpdateTaskRequest request, String token) {
        if (agendaTareaRepository == null) {
            throw new ResourceNotFoundException("Tarea no encontrada con ID: " + id);
        }

        AgendaTarea tarea = agendaTareaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con ID: " + id));

        // Si se cambia o agrega un leadId, validar que pertenezca al usuario
        if (request.getLeadId() != null && !Objects.equals(request.getLeadId(), tarea.getLeadId())) {
            if (leadValidationService != null) {
                leadValidationService.validarLeadPerteneceAUsuario(request.getLeadId(), token);
            }
        }

        tarea.setTitulo(request.getTitulo());
        tarea.setDescripcion(request.getDescripcion());
        tarea.setLeadId(request.getLeadId());
        tarea.setFechaLimite(request.getFechaLimite());
        tarea.setHoraLimite(request.getHoraLimite());
        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            tarea.setEstado(request.getEstado().toUpperCase());
        }
        tarea.setActualizadoEn(Instant.now());

        AgendaTarea actualizada = agendaTareaRepository.save(tarea);
        return TaskResponse.fromEntity(actualizada);
    }

    @Transactional
    public TaskResponse marcarComoCompletada(Long id, Long usuarioId) {
        if (agendaTareaRepository == null) {
            throw new ResourceNotFoundException("Tarea no encontrada con ID: " + id);
        }

        AgendaTarea tarea = agendaTareaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con ID: " + id));

        tarea.setEstado("COMPLETADA");
        tarea.setActualizadoEn(Instant.now());

        AgendaTarea actualizada = agendaTareaRepository.save(tarea);
        return TaskResponse.fromEntity(actualizada);
    }

    @Transactional
    public void eliminarTarea(Long id, Long usuarioId) {
        if (agendaTareaRepository == null) {
            throw new ResourceNotFoundException("Tarea no encontrada con ID: " + id);
        }

        AgendaTarea tarea = agendaTareaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con ID: " + id));

        agendaTareaRepository.delete(tarea);
    }
}
