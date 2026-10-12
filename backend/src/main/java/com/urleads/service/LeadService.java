package com.urleads.service;

import java.util.List;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.UpdateLeadRequest;

public interface LeadService {

    List<LeadResponse> listar(Long usuarioId);

    LeadResponse obtener(Long leadId, Long usuarioId);

    LeadResponse crear(
            Long usuarioId,
            CreateLeadRequest request);

    LeadResponse actualizar(
            Long leadId,
            Long usuarioId,
            UpdateLeadRequest request);

    void eliminar(
            Long leadId,
            Long usuarioId);
}