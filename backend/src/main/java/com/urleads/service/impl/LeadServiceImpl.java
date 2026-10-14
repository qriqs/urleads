package com.urleads.service.impl;

import org.springframework.stereotype.Service;

import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.LeadService;

import lombok.RequiredArgsConstructor;
import java.util.List;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;

import com.urleads.exception.LeadNotFoundException;

@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    private final LeadRepository leadRepository;
    private final UsuarioRepository usuarioRepository;
    
    @Override
    public List<LeadResponse> listar(Long usuarioId) {

        return leadRepository
                .findByUsuarioId(usuarioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    @Override
    public LeadResponse obtener(
            Long leadId,
            Long usuarioId) {

    	Lead lead = leadRepository
    	        .findByIdAndUsuarioId(
    	                leadId,
    	                usuarioId)
    	        .orElseThrow(() ->
    	                new LeadNotFoundException(
    	                        "Lead no encontrado"));

        return toResponse(lead);
    }
    @Override
    public LeadResponse crear(
            Long usuarioId,
            CreateLeadRequest request) {

        if ((request.getTelefono() == null
                || request.getTelefono().isBlank())
            &&
            (request.getCorreo() == null
                || request.getCorreo().isBlank())) {

            throw new IllegalArgumentException(
                    "Debe registrar teléfono o correo");
        }

        Usuario usuario = usuarioRepository
                .findById(usuarioId)
                .orElseThrow();

        Lead lead = new Lead();

        lead.setUsuario(usuario);
        lead.setNombre(request.getNombre());
        lead.setTelefono(request.getTelefono());
        lead.setCorreo(request.getCorreo());
        lead.setEtapa(request.getEtapa());
        lead.setService(
                request.getService());
        lead.setProximoSeguimiento(
                request.getProximoSeguimiento());
        

        Lead leadGuardado =
                leadRepository.save(lead);

        return toResponse(leadGuardado);
    }


    @Override
    public LeadResponse actualizar(
            Long leadId,
            Long usuarioId,
            UpdateLeadRequest request) {

        if ((request.getTelefono() == null
                || request.getTelefono().isBlank())
            &&
            (request.getCorreo() == null
                || request.getCorreo().isBlank())) {

            throw new IllegalArgumentException(
                    "Debe registrar teléfono o correo");
        }

        Lead lead = leadRepository
                .findByIdAndUsuarioId(
                        leadId,
                        usuarioId)
                .orElseThrow(() ->
                        new LeadNotFoundException(
                                "Lead no encontrado"));

        lead.setNombre(
                request.getNombre());

        lead.setTelefono(
                request.getTelefono());

        lead.setCorreo(
                request.getCorreo());

        lead.setEtapa(
                request.getEtapa());
        lead.setService(
                request.getService());

        lead.setProximoSeguimiento(
                request.getProximoSeguimiento());

        Lead leadActualizado =
                leadRepository.save(lead);

        return toResponse(leadActualizado);
    }
    @Override
    public void eliminar(
            Long leadId,
            Long usuarioId) {

    	Lead lead = leadRepository
    	        .findByIdAndUsuarioId(
    	                leadId,
    	                usuarioId)
    	        .orElseThrow(() ->
    	                new LeadNotFoundException(
    	                        "Lead no encontrado"));

        leadRepository.delete(lead);
    }
    private LeadResponse toResponse(Lead lead) {

        LeadResponse response = new LeadResponse();

        response.setId(lead.getId());
        response.setNombre(lead.getNombre());
        response.setTelefono(lead.getTelefono());
        response.setCorreo(lead.getCorreo());
        response.setEtapa(lead.getEtapa());
        response.setProximoSeguimiento(
                lead.getProximoSeguimiento());

        response.setCreadoEn(
                lead.getCreadoEn());

        response.setActualizadoEn(
                lead.getActualizadoEn());
        response.setService(
                lead.getService());

        return response;
    }

}