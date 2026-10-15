package com.urleads.service;

import com.urleads.dto.DashboardResponse;
import com.urleads.dto.DashboardResponse.LeadSeguimientoResumen;
import com.urleads.dto.DashboardResponse.SeguimientoMetricas;
import com.urleads.entity.Lead;
import com.urleads.repository.LeadRepository;
import com.urleads.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final LeadRepository leadRepository;
    private final CurrentUserProvider currentUserProvider;

    public DashboardService(LeadRepository leadRepository, CurrentUserProvider currentUserProvider) {
        this.leadRepository = leadRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public DashboardResponse obtenerMetricas() {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        LocalDate hoyLima = LocalDate.now(ZONA_LIMA);

        long totalLeads = leadRepository.countByUsuarioId(usuarioId);

        Map<String, Long> leadsPorEtapa = new HashMap<>();
        List<Object[]> agrupados = leadRepository.countLeadsPorEtapa(usuarioId);
        for (Object[] fila : agrupados) {
            leadsPorEtapa.put(fila[0].toString(), (Long) fila[1]);
        }

        List<Lead> leadsHoy = leadRepository.findSeguimientosHoy(usuarioId, hoyLima);
        List<Lead> leadsAtrasados = leadRepository.findSeguimientosAtrasados(usuarioId, hoyLima);

        SeguimientoMetricas metricas = new SeguimientoMetricas(
                leadsHoy.size(),
                leadsAtrasados.size(),
                leadsHoy.stream().map(this::mapResumen).toList(),
                leadsAtrasados.stream().map(this::mapResumen).toList()
        );

        return new DashboardResponse(totalLeads, leadsPorEtapa, metricas);
    }

    private LeadSeguimientoResumen mapResumen(Lead lead) {
        return new LeadSeguimientoResumen(
                lead.getId(),
                lead.getNombre(),
                lead.getEtapa(),
                lead.getProximoSeguimiento()
        );
    }
}