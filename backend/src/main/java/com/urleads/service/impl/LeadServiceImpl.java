package com.urleads.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.config.ClockConfig;
import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.dto.LeadAuditResponse;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.LeadAuditLog;
import com.urleads.entity.LeadStage;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.UsuarioNotFoundException;
import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.LeadService;
import com.urleads.service.LeadValidator;
import com.urleads.service.OutboxService;

@Service
@Transactional
public class LeadServiceImpl implements LeadService {

    private static final int PAGE_SIZE = 20;

    private final LeadRepository leadRepository;
    private final UsuarioRepository usuarioRepository;
    private final OutboxService outboxService;
    private final LeadAuditLogRepository leadAuditLogRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) OutboxService outboxService,
                           LeadAuditLogRepository leadAuditLogRepository,
                           Clock clock) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository must not be null");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository must not be null");
        this.outboxService = outboxService;
        this.leadAuditLogRepository = Objects.requireNonNull(leadAuditLogRepository, "leadAuditLogRepository must not be null");
        this.clock = clock != null ? clock : Clock.system(ClockConfig.LIMA_ZONE);
    }

    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           OutboxService outboxService,
                           LeadAuditLogRepository leadAuditLogRepository) {
        this(leadRepository, usuarioRepository, outboxService, leadAuditLogRepository, Clock.system(ClockConfig.LIMA_ZONE));
    }

    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           LeadAuditLogRepository leadAuditLogRepository,
                           Clock clock) {
        this(leadRepository, usuarioRepository, null, leadAuditLogRepository, clock);
    }

    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           LeadAuditLogRepository leadAuditLogRepository) {
        this(leadRepository, usuarioRepository, null, leadAuditLogRepository, Clock.system(ClockConfig.LIMA_ZONE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadResponse> listar(Long usuarioId) {
        return leadRepository.findByUsuarioId(usuarioId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listarPaginado(
            Long usuarioId, String search, String followUp, String stage, int page) {

        LocalDate hoy = LocalDate.now(clock.withZone(ClockConfig.LIMA_ZONE));

        String patron = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";

        String etapa = "";
        if (stage != null && !stage.isBlank()) {
            String etapaTrimmed = stage.trim().toUpperCase();
            if (!LeadStage.isValid(etapaTrimmed)) {
                throw new LeadValidationException("Etapa no válida para filtro: solo se permite NUEVO, EN_SEGUIMIENTO o CERRADO");
            }
            etapa = etapaTrimmed;
        }

        String modo = "all";
        if (followUp != null && !followUp.isBlank()) {
            String modoTrimmed = followUp.trim().toLowerCase();
            if (!modoTrimmed.equals("all") && !modoTrimmed.equals("today") && !modoTrimmed.equals("overdue")) {
                throw new LeadValidationException("Filtro followUp no válido: solo se permite today, overdue o all");
            }
            modo = modoTrimmed;
        }

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "creadoEn"));

        Page<Lead> resultado = leadRepository.buscar(usuarioId, etapa, patron, modo, hoy, pageable);

        return PageResponse.from(resultado.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public LeadResponse obtener(Long leadId, Long usuarioId) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        return toResponse(lead);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalLeadResponse obtenerInterno(Long leadId, Long usuarioId) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        return new InternalLeadResponse(
                lead.getId(),
                lead.getEtapa(),
                lead.getProximoSeguimiento()
        );
    }

    @Override
    public LeadResponse crear(Long usuarioId, CreateLeadRequest request) {
        LeadValidator.validate(request);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

        Lead lead = new Lead();
        lead.setUsuario(usuario);
        lead.setNombre(request.getNombre());
        lead.setTelefono(request.getTelefono());
        lead.setCorreo(request.getCorreo());
        lead.setService(request.getService());
        lead.setEtapa(request.getEtapa().trim());

        if ("CERRADO".equals(lead.getEtapa())) {
            lead.setProximoSeguimiento(null);
        } else {
            lead.setProximoSeguimiento(request.getProximoSeguimiento());
        }

        Lead leadGuardado = leadRepository.save(lead);
        if (outboxService != null) {
            outboxService.recordLeadCreated(usuarioId, leadGuardado);
        }
        return toResponse(leadGuardado);
    }

    @Override
    public LeadResponse actualizar(Long leadId, Long usuarioId, UpdateLeadRequest request) {
        LeadValidator.validate(request);

        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        String oldNombre = lead.getNombre();
        String oldTelefono = lead.getTelefono();
        String oldCorreo = lead.getCorreo();
        String oldService = lead.getService();
        String oldEtapa = lead.getEtapa();
        LocalDate oldProximoSeguimiento = lead.getProximoSeguimiento();

        String newNombre = request.getNombre();
        String newTelefono = request.getTelefono();
        String newCorreo = request.getCorreo();
        String newService = request.getService();
        String newEtapa = request.getEtapa().trim();
        LocalDate newProximoSeguimiento = "CERRADO".equals(newEtapa) ? null : request.getProximoSeguimiento();

        lead.setNombre(newNombre);
        lead.setTelefono(newTelefono);
        lead.setCorreo(newCorreo);
        lead.setService(newService);
        lead.setEtapa(newEtapa);
        lead.setProximoSeguimiento(newProximoSeguimiento);

        Lead leadActualizado = leadRepository.save(lead);

        recordAuditLogs(leadId, usuarioId,
                oldNombre, newNombre,
                oldTelefono, newTelefono,
                oldCorreo, newCorreo,
                oldService, newService,
                oldEtapa, newEtapa,
                oldProximoSeguimiento, newProximoSeguimiento);

        if (outboxService != null) {
            outboxService.recordLeadUpdated(usuarioId, leadActualizado);
        }
        return toResponse(leadActualizado);
    }

    private void recordAuditLogs(Long leadId, Long usuarioId,
                                 String oldNombre, String newNombre,
                                 String oldTelefono, String newTelefono,
                                 String oldCorreo, String newCorreo,
                                 String oldService, String newService,
                                 String oldEtapa, String newEtapa,
                                 LocalDate oldProximoSeguimiento, LocalDate newProximoSeguimiento) {
        List<LeadAuditLog> deltas = new ArrayList<>();
        OffsetDateTime nowUtc = Instant.now(clock).atOffset(ZoneOffset.UTC);

        if (!Objects.equals(oldNombre, newNombre)) {
            deltas.add(new LeadAuditLog(leadId, usuarioId, "name", oldNombre, newNombre, nowUtc));
        }
        if (!Objects.equals(oldTelefono, newTelefono)) {
            deltas.add(new LeadAuditLog(leadId, usuarioId, "phone", oldTelefono, newTelefono, nowUtc));
        }
        if (!Objects.equals(oldCorreo, newCorreo)) {
            deltas.add(new LeadAuditLog(leadId, usuarioId, "email", oldCorreo, newCorreo, nowUtc));
        }
        if (!Objects.equals(oldService, newService)) {
            deltas.add(new LeadAuditLog(leadId, usuarioId, "service", oldService, newService, nowUtc));
        }
        if (!Objects.equals(oldEtapa, newEtapa)) {
            deltas.add(new LeadAuditLog(leadId, usuarioId, "stage", oldEtapa, newEtapa, nowUtc));
        }

        if (!Objects.equals(oldProximoSeguimiento, newProximoSeguimiento)) {
            String oldVal = oldProximoSeguimiento != null ? oldProximoSeguimiento.toString() : null;
            String newVal = newProximoSeguimiento != null ? newProximoSeguimiento.toString() : null;
            deltas.add(new LeadAuditLog(leadId, usuarioId, "nextFollowUp", oldVal, newVal, nowUtc));
        }

        if (!deltas.isEmpty()) {
            leadAuditLogRepository.saveAll(deltas);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadAuditResponse> listarAuditoria(Long leadId, Long usuarioId, int page) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<LeadAuditLog> resultado = leadAuditLogRepository.findByLeadIdAndUsuarioId(
                lead.getId(), usuarioId, pageable);

        return PageResponse.from(resultado.map(this::toAuditResponse));
    }

    private LeadAuditResponse toAuditResponse(LeadAuditLog audit) {
        return new LeadAuditResponse(
                audit.getId(),
                audit.getLeadId(),
                audit.getFieldName(),
                audit.getOldValue(),
                audit.getNewValue(),
                audit.getCreatedAt());
    }

    @Override
    public void eliminar(Long leadId, Long usuarioId) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        Long idEliminado = lead.getId();
        leadRepository.delete(lead);
        if (outboxService != null) {
            outboxService.recordLeadDeleted(usuarioId, idEliminado);
        }
    }

    private LeadResponse toResponse(Lead lead) {
        LeadResponse response = new LeadResponse();
        response.setId(lead.getId());
        response.setNombre(lead.getNombre());
        response.setTelefono(lead.getTelefono());
        response.setCorreo(lead.getCorreo());
        response.setService(lead.getService());
        response.setEtapa(lead.getEtapa());
        response.setProximoSeguimiento(lead.getProximoSeguimiento());
        response.setCreadoEn(lead.getCreadoEn());
        response.setActualizadoEn(lead.getActualizadoEn());

        return response;
    }
}