package com.urleads.comercial.client;

import com.urleads.comercial.client.dto.LeadInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "crm-service",
    url = "${crm.service.url:}"
)
public interface CrmClient {

    @GetMapping("/internal/v1/leads/{id}")
    LeadInternalResponse getLeadById(@PathVariable("id") Long id);
}
