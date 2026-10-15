package com.urleads.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long totalLeads,
        Map<String, Long> leadsPorEtapa,
        SeguimientoMetricas seguimientos
) {
    public record SeguimientoMetricas(
            long hoy,
            long atrasados,
            List<LeadSeguimientoResumen> leadsHoy,
            List<LeadSeguimientoResumen> leadsAtrasados
    ) {}

    public record LeadSeguimientoResumen(
            Long id,
            String nombre,
            String etapa,
            LocalDate proximoSeguimiento
    ) {}
}