package com.urleads.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urleads.dto.InternalLeadResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadService;

/**
 * Controlador REST para la consulta interna de leads entre microservicios (Backend V2 C03).
 * Protegido bajo /internal/** con autenticación Bearer JWT RS256, audiencia crm-api
 * y scope crm.leads.read. Resuelve el propietario exclusivamente a través de CurrentUserProvider.
 */
@RestController
@RequestMapping("/internal/v1/leads")
public class InternalLeadController {

    private final LeadService leadService;
    private final CurrentUserProvider currentUserProvider;

    public InternalLeadController(LeadService leadService, CurrentUserProvider currentUserProvider) {
        this.leadService = leadService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{id}")
    public InternalLeadResponse obtenerInterno(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return leadService.obtenerInterno(id, usuarioId);
    }
}
