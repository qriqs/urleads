package com.urleads.service;

import com.urleads.dto.NotaRequest;
import com.urleads.dto.NotaResponse;
import com.urleads.entity.Lead;
import com.urleads.entity.Nota;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NotaService {

    private final NotaRepository notaRepository;
    private final LeadRepository leadRepository;
    private final UsuarioRepository usuarioRepository;

    public NotaService(NotaRepository notaRepository,
                       LeadRepository leadRepository,
                       UsuarioRepository usuarioRepository) {
        this.notaRepository = notaRepository;
        this.leadRepository = leadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<NotaResponse> listarNotas(Long leadId, String username) {
        Usuario usuario = obtenerUsuario(username);

        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado"));
        if (lead.isArchived()) {
            throw new ResourceNotFoundException("Lead no encontrado");
        }

        return notaRepository.findByLeadIdOrderByCreadoEnAsc(leadId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public NotaResponse crearNota(Long leadId, NotaRequest request, String username) {
        Usuario usuario = obtenerUsuario(username);

        if (request.content() == null || request.content().isBlank() || request.content().length() > 2000) {
            throw new IllegalArgumentException("El contenido de la nota es inválido");
        }

        Lead lead = leadRepository.findByIdAndUsuarioId(leadId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado"));
        if (lead.isArchived()) {
            throw new ResourceNotFoundException("Lead no encontrado");
        }

        Nota nota = new Nota();
        nota.setLead(lead);
        nota.setContenido(request.content());
        nota.setCreadoEn(OffsetDateTime.now());

        Nota guardada = notaRepository.save(nota);
        return mapToResponse(guardada);
    }

    private Usuario obtenerUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no autenticado"));
    }

    private NotaResponse mapToResponse(Nota nota) {
        return new NotaResponse(
                nota.getId(),
                nota.getContenido(),
                nota.getCreadoEn()
        );
    }
}