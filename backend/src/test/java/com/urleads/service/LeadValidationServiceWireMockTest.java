package com.urleads.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.urleads.dto.LeadInternalResponse;
import com.urleads.exception.InvalidLeadAssociationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeadValidationServiceWireMockTest {

    private static WireMockServer wireMockServer;
    private static LeadValidationService leadValidationService;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        leadValidationService = new LeadValidationService(wireMockServer.baseUrl());
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("WireMock 200 OK: lead existe y pertenece al usuario, validación exitosa")
    void validarLead_leadPropio_retornaRespuestaExitosa() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/v1/leads/105"))
                .withHeader("Authorization", equalTo("Bearer valid-jwt-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 105,
                                  "stage": "EN_SEGUIMIENTO",
                                  "nextFollowUp": "2026-10-25"
                                }
                                """)));

        LeadInternalResponse response = leadValidationService.validarLeadPerteneceAUsuario(105L, "Bearer valid-jwt-token");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(105L);
        assertThat(response.getStage()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(response.getNextFollowUp()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    @DisplayName("WireMock 404 Not Found: lead no existe o es ajeno, lanza InvalidLeadAssociationException")
    void validarLead_leadAjenoOInexistente_lanzaExcepcion() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/v1/leads/999"))
                .withHeader("Authorization", equalTo("Bearer valid-jwt-token"))
                .willReturn(aResponse().withStatus(404)));

        InvalidLeadAssociationException ex = assertThrows(
                InvalidLeadAssociationException.class,
                () -> leadValidationService.validarLeadPerteneceAUsuario(999L, "Bearer valid-jwt-token")
        );

        assertThat(ex.getMessage()).contains("El lead no existe o no pertenece al usuario autenticado");
    }

    @Test
    @DisplayName("leadId nulo no invoca WireMock y retorna null (asociación opcional)")
    void validarLead_leadIdNulo_retornaNullSinLlamarHttp() {
        LeadInternalResponse response = leadValidationService.validarLeadPerteneceAUsuario(null, "Bearer token");
        assertThat(response).isNull();
        wireMockServer.verify(0, getRequestedFor(urlMatching("/internal/v1/leads/.*")));
    }
}
