package com.urleads.service;

import java.util.List;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.dto.LeadAuditResponse;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;

public interface LeadService {

    List<LeadResponse> listar(Long usuarioId);

    PageResponse<LeadResponse> listarPaginado(
            Long usuarioId,
            String search,
            String followUp,
            String stage,
            int page);

    LeadResponse obtener(Long leadId, Long usuarioId);

    InternalLeadResponse obtenerInterno(Long leadId, Long usuarioId);

    LeadResponse crear(Long usuarioId, CreateLeadRequest request);

    LeadResponse actualizar(Long leadId, Long usuarioId, UpdateLeadRequest request);

    void eliminar(Long leadId, Long usuarioId);

    PageResponse<LeadAuditResponse> listarAuditoria(Long leadId, Long usuarioId, int page);
}