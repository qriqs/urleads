package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import com.urleads.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.urleads.dto.DashboardResponse;
import com.urleads.entity.Lead;
import com.urleads.repository.LeadRepository;
import com.urleads.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Long USUARIO_ID = 1L;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USUARIO_ID);
    }

    @Test
    @DisplayName("Debe devolver métricas en cero y listas vacías cuando el usuario no tiene leads")
    void obtenerMetricas_usuarioSinLeads_retornaRespuestaVaciaValida() {
        LocalDate hoyLima = LocalDate.now(ZONA_LIMA);

        when(leadRepository.countByUsuarioId(USUARIO_ID)).thenReturn(0L);
        when(leadRepository.countLeadsPorEtapa(USUARIO_ID)).thenReturn(Collections.emptyList());
        when(leadRepository.findSeguimientosHoy(USUARIO_ID, hoyLima)).thenReturn(Collections.emptyList());
        when(leadRepository.findSeguimientosAtrasados(USUARIO_ID, hoyLima)).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.obtenerMetricas();

        assertThat(response).isNotNull();
        assertThat(response.totalLeads()).isZero();
        assertThat(response.leadsPorEtapa()).isNotNull().isEmpty();
        
        assertThat(response.seguimientos()).isNotNull();
        assertThat(response.seguimientos().hoy()).isZero();
        assertThat(response.seguimientos().atrasados()).isZero();
        assertThat(response.seguimientos().leadsHoy()).isNotNull().isEmpty();
        assertThat(response.seguimientos().leadsAtrasados()).isNotNull().isEmpty();

        verify(leadRepository).countByUsuarioId(USUARIO_ID);
    }

    @Test
    @DisplayName("Debe calcular métricas y clasificar seguimientos de hoy y atrasados correctamente")
    void obtenerMetricas_conLeadsValidos_retornaMetricasCorrectas() {
        LocalDate hoyLima = LocalDate.now(ZONA_LIMA);

        Lead leadHoy = new Lead();
        leadHoy.setId(10L);
        leadHoy.setNombre("Lead Contacto Hoy");
        leadHoy.setEtapa("CONTACTADO");
        leadHoy.setProximoSeguimiento(hoyLima);

        Lead leadAtrasado = new Lead();
        leadAtrasado.setId(11L);
        leadAtrasado.setNombre("Lead Seguimiento Ayer");
        leadAtrasado.setEtapa("EN_PROCESO");
        leadAtrasado.setProximoSeguimiento(hoyLima.minusDays(1));

        List<Object[]> agrupados = List.of(
                new Object[]{"NUEVO", 3L},
                new Object[]{"CONTACTADO", 1L},
                new Object[]{"EN_PROCESO", 1L}
        );

        when(leadRepository.countByUsuarioId(USUARIO_ID)).thenReturn(5L);
        when(leadRepository.countLeadsPorEtapa(USUARIO_ID)).thenReturn(agrupados);
        when(leadRepository.findSeguimientosHoy(USUARIO_ID, hoyLima)).thenReturn(List.of(leadHoy));
        when(leadRepository.findSeguimientosAtrasados(USUARIO_ID, hoyLima)).thenReturn(List.of(leadAtrasado));

        DashboardResponse response = dashboardService.obtenerMetricas();

        assertThat(response.totalLeads()).isEqualTo(5L);
        assertThat(response.leadsPorEtapa())
                .containsEntry("NUEVO", 3L)
                .containsEntry("CONTACTADO", 1L)
                .containsEntry("EN_PROCESO", 1L);

        assertThat(response.seguimientos().hoy()).isEqualTo(1L);
        assertThat(response.seguimientos().atrasados()).isEqualTo(1L);

        assertThat(response.seguimientos().leadsHoy()).hasSize(1);
        assertThat(response.seguimientos().leadsHoy().get(0).id()).isEqualTo(10L);
        assertThat(response.seguimientos().leadsHoy().get(0).etapa()).isEqualTo("CONTACTADO");

        assertThat(response.seguimientos().leadsAtrasados()).hasSize(1);
        assertThat(response.seguimientos().leadsAtrasados().get(0).id()).isEqualTo(11L);
        assertThat(response.seguimientos().leadsAtrasados().get(0).etapa()).isEqualTo("EN_PROCESO");
    }

    @Test
    @DisplayName("Debe consultar siempre utilizando la fecha calculada en la zona horaria America/Lima")
    void obtenerMetricas_usaFechaDeZonaHorariaLima() {
        LocalDate hoyLima = LocalDate.now(ZONA_LIMA);

        when(leadRepository.countByUsuarioId(USUARIO_ID)).thenReturn(0L);
        when(leadRepository.countLeadsPorEtapa(USUARIO_ID)).thenReturn(Collections.emptyList());
        when(leadRepository.findSeguimientosHoy(eq(USUARIO_ID), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(leadRepository.findSeguimientosAtrasados(eq(USUARIO_ID), any(LocalDate.class))).thenReturn(Collections.emptyList());

        dashboardService.obtenerMetricas();

        verify(leadRepository).findSeguimientosHoy(USUARIO_ID, hoyLima);
        verify(leadRepository).findSeguimientosAtrasados(USUARIO_ID, hoyLima);
    }
}