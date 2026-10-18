package com.urleads.soap.endpoint;

import com.urleads.dto.InternalLeadResponse;
import com.urleads.service.LeadService;
import com.urleads.soap.dto.GetLeadRequest;
import com.urleads.soap.dto.GetLeadResponse;
import com.urleads.soap.dto.StageType;
import com.urleads.soap.exception.SoapClientFaultException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * Endpoint SOAP 1.1 para la consulta formal de leads (Backend V2 C13).
 * Resuelve la operación GetLead sobre el namespace urn:urleads:crm:leads:v1.
 * Extrae el propietario exclusivamente desde el claim 'sub' del JWT validado
 * y reutiliza directamente LeadService.obtenerInterno(leadId, usuarioId) sin
 * realizar autoconsumo HTTP contra CRM.
 */
@Endpoint
public class LeadSoapEndpoint {

    public static final String NAMESPACE_URI = "urn:urleads:crm:leads:v1";

    private final LeadService leadService;

    public LeadSoapEndpoint(LeadService leadService) {
        this.leadService = leadService;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "GetLeadRequest")
    @ResponsePayload
    public GetLeadResponse getLead(@RequestPayload GetLeadRequest request) {
        if (request == null || request.getLeadId() == null || request.getLeadId() <= 0) {
            throw new SoapClientFaultException("Identificador de lead inválido o no positivo");
        }

        Long usuarioId = resolveAuthenticatedUsuarioId();

        InternalLeadResponse internalLead = leadService.obtenerInterno(request.getLeadId(), usuarioId);

        StageType stage;
        try {
            stage = StageType.valueOf(internalLead.stage());
        } catch (IllegalArgumentException | NullPointerException e) {
            stage = StageType.NUEVO;
        }

        return new GetLeadResponse(
                internalLead.id(),
                stage,
                internalLead.nextFollowUp()
        );
    }

    private Long resolveAuthenticatedUsuarioId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new SoapClientFaultException("Usuario no autenticado");
        }

        String sub = jwt.getSubject();
        if (sub == null || !sub.matches("^[0-9]+$")) {
            throw new SoapClientFaultException("Identificador de sujeto (sub) inválido");
        }

        try {
            long id = Long.parseLong(sub);
            if (id <= 0) {
                throw new SoapClientFaultException("Identificador de usuario debe ser positivo");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new SoapClientFaultException("Identificador de usuario excede rango numérico");
        }
    }
}
