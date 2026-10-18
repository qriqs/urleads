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
            String priority,
            String origin,
            List<Long> tagIds,
            int page);

    default PageResponse<LeadResponse> listarPaginado(
            Long usuarioId, String search, String followUp, String stage,
            String priority, String origin, int page) {
        return listarPaginado(usuarioId, search, followUp, stage, priority, origin, null, page);
    }

    default PageResponse<LeadResponse> listarPaginado(
            Long usuarioId, String search, String followUp, String stage,
            List<Long> tagIds, int page) {
        return listarPaginado(usuarioId, search, followUp, stage, null, null, tagIds, page);
    }

    default PageResponse<LeadResponse> listarPaginado(
            Long usuarioId,
            String search,
            String followUp,
            String stage,
            int page) {
        return listarPaginado(usuarioId, search, followUp, stage, null, null, null, page);
    }

    LeadResponse obtener(Long leadId, Long usuarioId);

    InternalLeadResponse obtenerInterno(Long leadId, Long usuarioId);

    LeadResponse crear(Long usuarioId, CreateLeadRequest request);

    LeadResponse actualizar(Long leadId, Long usuarioId, UpdateLeadRequest request);

    PageResponse<LeadAuditResponse> listarAuditoria(Long leadId, Long usuarioId, int page);

    LeadResponse actualizarTags(Long leadId, Long usuarioId, List<Long> tagIds);

    LeadResponse archivar(Long leadId, Long usuarioId);

    LeadResponse restaurar(Long leadId, Long usuarioId);

    PageResponse<LeadResponse> listarArchivados(Long usuarioId, int page);

    void eliminar(Long leadId, Long usuarioId);
}
