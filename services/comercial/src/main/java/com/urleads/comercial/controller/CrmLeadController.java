package com.urleads.comercial.controller;

import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.client.dto.LeadInternalResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crm/leads")
public class CrmLeadController {

    private final CrmLeadService crmLeadService;

    public CrmLeadController(CrmLeadService crmLeadService) {
        this.crmLeadService = crmLeadService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadInternalResponse> getLead(@PathVariable Long id) {
        LeadInternalResponse lead = crmLeadService.getLeadById(id);
        return ResponseEntity.ok(lead);
    }
}
