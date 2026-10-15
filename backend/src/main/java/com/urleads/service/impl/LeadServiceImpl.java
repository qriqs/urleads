package com.urleads.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.UsuarioNotFoundException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.LeadService;
import com.urleads.service.LeadValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class LeadServiceImpl implements LeadService {

    private static final int PAGE_SIZE = 20;

    private final LeadRepository leadRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LeadResponse> listar(Long usuarioId) {
        return leadRepository.findByUsuarioId(usuarioId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listarPaginado(
            Long usuarioId, String search, String followUp, String stage, int page) {

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));

        String patron = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";
        String etapa = (stage == null || stage.isBlank()) ? "" : stage.trim().toUpperCase();
        String modo = (followUp == null || followUp.isBlank()) ? "all" : followUp.trim().toLowerCase();

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
        lead.setEtapa(request.getEtapa());
        lead.setProximoSeguimiento(request.getProximoSeguimiento());

        Lead leadGuardado = leadRepository.save(lead);
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
        lead.setEtapa(request.getEtapa());
        lead.setProximoSeguimiento(request.getProximoSeguimiento());

        Lead leadActualizado = leadRepository.save(lead);
        return toResponse(leadActualizado);
    }

    @Override
    public void eliminar(Long leadId, Long usuarioId) {
        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuarioId)
                .orElseThrow(() -> new LeadNotFoundException("Lead no encontrado"));

        leadRepository.delete(lead);
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