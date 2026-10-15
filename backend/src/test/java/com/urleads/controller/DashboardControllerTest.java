package com.urleads.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.urleads.dto.DashboardResponse;
import com.urleads.dto.DashboardResponse.SeguimientoMetricas;
import com.urleads.service.DashboardService;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser
    @DisplayName("GET /api/dashboard retorna 200 con el contrato JSON esperado")
    void getDashboardMetrics_retorna200ConJson() throws Exception {
        SeguimientoMetricas seguimientos = new SeguimientoMetricas(
                0L,
                0L,
                Collections.emptyList(),
                Collections.emptyList()
        );
        DashboardResponse mockResponse = new DashboardResponse(0L, Map.of(), seguimientos);

        when(dashboardService.obtenerMetricas()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(0))
                .andExpect(jsonPath("$.leadsPorEtapa").isMap())
                .andExpect(jsonPath("$.seguimientos.hoy").value(0))
                .andExpect(jsonPath("$.seguimientos.atrasados").value(0))
                .andExpect(jsonPath("$.seguimientos.leadsHoy").isArray())
                .andExpect(jsonPath("$.seguimientos.leadsAtrasados").isArray());
    }
}