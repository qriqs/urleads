package com.urleads.controller;

import com.urleads.dto.LeadInternalResponse;
import com.urleads.entity.Lead;
import com.urleads.repository.LeadRepository;
import com.urleads.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Endpoint interno para la validación de leads entre servicios (CRM -> Comercial / Agenda).
 * Contrato acordado en contratos.md sección 3: GET /internal/v1/leads/{id}
 */
@RestController
@RequestMapping("/internal/v1/leads")
public class InternalLeadController {

    private final LeadRepository leadRepository;
    private final CurrentUserProvider currentUserProvider;

    public InternalLeadController(
            @Autowired(required = false) LeadRepository leadRepository,
            @Autowired(required = false) CurrentUserProvider currentUserProvider
    ) {
        this.leadRepository = leadRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadInternalResponse> getInternalLead(@PathVariable Long id) {
        if (currentUserProvider == null || leadRepository == null) {
            return ResponseEntity.notFound().build();
        }

        Long usuarioId = currentUserProvider.getCurrentUserId();
        if (usuarioId == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Lead> leadOpt = leadRepository.findByIdAndUsuarioId(id, usuarioId);
        if (leadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Lead lead = leadOpt.get();
        LeadInternalResponse response = LeadInternalResponse.builder()
                .id(lead.getId())
                .stage(lead.getEtapa())
                .nextFollowUp(lead.getProximoSeguimiento())
                .build();

        return ResponseEntity.ok(response);
    }
}
