package com.urleads.comercial.client;

import com.urleads.comercial.client.dto.LeadInternalResponse;
import com.urleads.comercial.client.exception.CrmUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CrmLeadService {

    private static final Logger log = LoggerFactory.getLogger(CrmLeadService.class);
    private final CrmClient crmClient;

    public CrmLeadService(CrmClient crmClient) {
        this.crmClient = crmClient;
    }

    @CircuitBreaker(name = "crmService", fallbackMethod = "getLeadFallback")
    public LeadInternalResponse getLeadById(Long id) {
        return crmClient.getLeadById(id);
    }

    public LeadInternalResponse getLeadFallback(Long id, Throwable ex) {
        log.warn("Fallback de Circuit Breaker activado para lead {}: {}", id, ex.getMessage());
        if (ex instanceof RuntimeException re && !(ex instanceof CallNotPermittedException || ex instanceof feign.RetryableException)) {
            throw re;
        }
        throw new CrmUnavailableException("El servicio CRM no se encuentra disponible temporalmente.", ex);
    }
}
