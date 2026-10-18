package com.urleads.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.config.ClockConfig;
import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.TagResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.LeadStage;
import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.TagNotFoundException;
import com.urleads.exception.UsuarioNotFoundException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.TagRepository;
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
    private final TagRepository tagRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    @Autowired
    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           @Autowired(required = false) TagRepository tagRepository,
                           @Autowired(required = false) OutboxService outboxService,
                           Clock clock) {
        this.leadRepository = leadRepository;
        this.usuarioRepository = usuarioRepository;
        this.tagRepository = tagRepository;
        this.outboxService = outboxService;
        this.clock = clock != null ? clock : Clock.system(ClockConfig.LIMA_ZONE);
    }

    public LeadServiceImpl(LeadRepository leadRepository,
                           UsuarioRepository usuarioRepository,
                           OutboxService outboxService) {
        this(leadRepository, usuarioRepository, null, outboxService, Clock.system(ClockConfig.LIMA_ZONE));
    }

    public LeadServiceImpl(LeadRepository leadRepository, UsuarioRepository usuarioRepository, Clock clock) {
        this(leadRepository, usuarioRepository, null, null, clock);
    }

    public LeadServiceImpl(LeadRepository leadRepository, UsuarioRepository usuarioRepository) {
        this(leadRepository, usuarioRepository, null, null, Clock.system(ClockConfig.LIMA_ZONE));
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
        return listarPaginado(usuarioId, search, followUp, stage, null, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listarPaginado(
            Long usuarioId, String search, String followUp, String stage, List<Long> tagIds, int page) {

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

        List<Long> sanitizedTagIds = (tagIds == null) ? List.of() :
                tagIds.stream().filter(Objects::nonNull).distinct().toList();

        Page<Lead> resultado;
        if (sanitizedTagIds.isEmpty()) {
            resultado = leadRepository.buscar(usuarioId, etapa, patron, modo, hoy, pageable);
        } else {
            resultado = leadRepository.buscarConTags(usuarioId, etapa, patron, modo, hoy, sanitizedTagIds, pageable);
        }

        List<Lead> leadsEnPagina = resultado.getContent();
        if (leadsEnPagina.isEmpty()) {
            return PageResponse.from(resultado.map(this::toResponse));
        }

        List<Long> leadIds = leadsEnPagina.stream().map(Lead::getId).toList();
        List<Lead> leadsConTags = leadRepository.findAllWithTagsByIdIn(leadIds, usuarioId);

        Map<Long, List<TagResponse>> tagsPorLead = leadsConTags.stream()
                .collect(Collectors.toMap(
                        Lead::getId,
                        l -> l.getTags() == null ? List.of() : l.getTags().stream()
                                .sorted(Comparator.comparing(Tag::getNombre, String.CASE_INSENSITIVE_ORDER))
                                .map(t -> new TagResponse(t.getId(), t.getNombre(), t.getCreadoEn()))
                                .toList(),
                        (existing, replacement) -> existing
                ));

        return PageResponse.from(resultado.map(lead ->
                toResponse(lead, tagsPorLead.getOrDefault(lead.getId(), List.of()))));
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

        Lead leadActualizado = leadRepository.save(lead);
        if (outboxService != null) {
            outboxService.recordLeadUpdated(usuarioId, leadActualizado);
        }
        return toResponse(leadActualizado);
    }

    @Override
    public LeadResponse actualizarTags(Long leadId, Long usuarioId, List<Long> tagIds) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        if (tagIds == null) {
            throw new IllegalArgumentException("tagIds no puede ser nulo");
        }

        if (tagIds.isEmpty()) {
            lead.getTags().clear();
            Lead leadGuardado = leadRepository.save(lead);
            return toResponse(leadGuardado);
        }

        List<Long> distinctTagIds = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (tagRepository == null) {
            throw new IllegalStateException("TagRepository no configurado");
        }
        List<Tag> ownedTags = tagRepository.findByUsuarioIdAndIdIn(usuarioId, distinctTagIds);

        if (ownedTags.size() != distinctTagIds.size()) {
            throw new TagNotFoundException("Una o más etiquetas no existen o no pertenecen al usuario");
        }

        lead.setTags(new java.util.HashSet<>(ownedTags));
        Lead leadGuardado = leadRepository.save(lead);
        return toResponse(leadGuardado);
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
        List<TagResponse> tagResponses = (lead.getTags() == null) ? List.of() :
                lead.getTags().stream()
                        .sorted(Comparator.comparing(Tag::getNombre, String.CASE_INSENSITIVE_ORDER))
                        .map(t -> new TagResponse(t.getId(), t.getNombre(), t.getCreadoEn()))
                        .toList();
        return toResponse(lead, tagResponses);
    }

    private LeadResponse toResponse(Lead lead, List<TagResponse> tags) {
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
        response.setTags(tags);

        return response;
    }
}
