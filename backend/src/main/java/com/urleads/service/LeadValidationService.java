package com.urleads.service;

import com.urleads.dto.LeadInternalResponse;
import com.urleads.exception.InvalidLeadAssociationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class LeadValidationService {

    private static final Logger log = LoggerFactory.getLogger(LeadValidationService.class);

    private final RestTemplate restTemplate;
    private final String crmBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public LeadValidationService(
            @Value("${crm.base-url:http://localhost:8080}") String crmBaseUrl
    ) {
        this.crmBaseUrl = crmBaseUrl;
        this.restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    }

    public LeadValidationService(String crmBaseUrl, RestTemplate restTemplate) {
        this.crmBaseUrl = crmBaseUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Valida si el lead pertenece al usuario autenticado consultando el contrato interno de CRM.
     * Si leadId es nulo, no se realiza llamada (asociación de lead opcional).
     */
    public LeadInternalResponse validarLeadPerteneceAUsuario(Long leadId, String token) {
        if (leadId == null) {
            return null;
        }

        String url = crmBaseUrl + "/internal/v1/leads/" + leadId;
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            String bearerToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            headers.setBearerAuth(bearerToken);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<LeadInternalResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    LeadInternalResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado");
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Validación fallida: lead {} no existe o no pertenece al usuario (CRM 404)", leadId);
            throw new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Validación rechazada: credenciales no autorizadas para lead {}", leadId);
            throw new InvalidLeadAssociationException("No autorizado para consultar o vincular el lead especificado");
        } catch (Exception e) {
            if (e instanceof InvalidLeadAssociationException) {
                throw (InvalidLeadAssociationException) e;
            }
            log.error("Error al comunicarse con el servicio CRM para validar lead {}: {}", leadId, e.getMessage());
            throw new InvalidLeadAssociationException("Error al validar existencia y pertenencia del lead: " + e.getMessage());
        }
    }
}
