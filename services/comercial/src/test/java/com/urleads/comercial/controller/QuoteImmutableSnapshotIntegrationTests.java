package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.client.dto.LeadInternalResponse;
import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.OutboxEventRepository;
import com.urleads.comercial.repository.QuoteItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.security.JwtTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteImmutableSnapshotIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private QuoteItemRepository quoteItemRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private CrmLeadService crmLeadService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        quoteItemRepository.deleteAll();
        quoteRepository.deleteAll();
        catalogItemRepository.deleteAll();

        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    @Test
    @DisplayName("Catalog item modification does not alter previously emitted or draft quotes (Immutable Snapshot)")
    void catalogModification_preservesHistoricalQuoteSnapshots() throws Exception {
        // 1. Crear producto en catalogo: SRV-CLOUD a 150.00 USD
        CatalogItemRequest catReq1 = new CatalogItemRequest(
            "SRV-CLOUD",
            "Cloud Hosting",
            "Servidor en la nube 4 vCPU",
            "USD",
            new BigDecimal("150.00"),
            true
        );

        String catResStr = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(catReq1)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long catId = objectMapper.readTree(catResStr).get("id").asLong();

        // 2. Crear Cotizacion Q1 con 2 unidades de SRV-CLOUD:
        // Subtotal = 2 * 150.00 = 300.00, Tax (18%) = 54.00, Total = 354.00
        QuoteCreateRequest q1Req = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotizacion original con precio base",
            List.of(
                new QuoteItemRequest(catId, null, null, null, new BigDecimal("2.00"), null)
            )
        );

        String q1ResStr = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(q1Req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(300.00))
            .andExpect(jsonPath("$.tax").value(54.00))
            .andExpect(jsonPath("$.total").value(354.00))
            .andExpect(jsonPath("$.items[0].name").value("Cloud Hosting"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(150.00))
            .andReturn().getResponse().getContentAsString();

        Long q1Id = objectMapper.readTree(q1ResStr).get("id").asLong();

        // 3. Emitir cotización Q1: DRAFT -> SENT
        mockMvc.perform(post("/api/v1/quotes/" + q1Id + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        // 4. Modificar el catalogo: cambiar nombre a "Cloud Hosting V2 Enterprise", precio a 450.00 USD (3x)
        CatalogItemRequest catReqUpdate = new CatalogItemRequest(
            "SRV-CLOUD",
            "Cloud Hosting V2 Enterprise",
            "Servidor en la nube 16 vCPU Mejorado",
            "USD",
            new BigDecimal("450.00"),
            true
        );

        mockMvc.perform(put("/api/v1/catalog/" + catId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(catReqUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cloud Hosting V2 Enterprise"))
            .andExpect(jsonPath("$.unitPrice").value(450.00));

        // 5. Verificar que Q1 permanece ESTRICTAMENTE INMUTABLE con los datos historicos
        mockMvc.perform(get("/api/v1/quotes/" + q1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(q1Id))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.subtotal").value(300.00))
            .andExpect(jsonPath("$.tax").value(54.00))
            .andExpect(jsonPath("$.total").value(354.00))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].name").value("Cloud Hosting"))
            .andExpect(jsonPath("$.items[0].description").value("Servidor en la nube 4 vCPU"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(150.00))
            .andExpect(jsonPath("$.items[0].subtotal").value(300.00));

        // 6. Crear una nueva cotización Q2 con el mismo producto del catalogo:
        // Debe tomar el NUEVO snapshot: 2 * 450.00 = 900.00, Tax = 162.00, Total = 1062.00
        QuoteCreateRequest q2Req = new QuoteCreateRequest(
            106L,
            "USD",
            new BigDecimal("0.1800"),
            "Nueva cotización con precio actualizado",
            List.of(
                new QuoteItemRequest(catId, null, null, null, new BigDecimal("2.00"), null)
            )
        );

        String q2ResStr = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(q2Req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(900.00))
            .andExpect(jsonPath("$.tax").value(162.00))
            .andExpect(jsonPath("$.total").value(1062.00))
            .andExpect(jsonPath("$.items[0].name").value("Cloud Hosting V2 Enterprise"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(450.00))
            .andReturn().getResponse().getContentAsString();

        Long q2Id = objectMapper.readTree(q2ResStr).get("id").asLong();

        // 7. Aceptar Q1 con CRM mockeado y verificar que el total aceptado es el historico (354.00)
        Mockito.when(crmLeadService.getLeadById(105L))
            .thenReturn(new LeadInternalResponse(105L, "EN_SEGUIMIENTO", LocalDate.of(2026, 10, 25)));

        mockMvc.perform(post("/api/v1/quotes/" + q1Id + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.total").value(354.00));

        // 8. Eliminar el item del catalogo
        mockMvc.perform(delete("/api/v1/catalog/" + catId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNoContent());

        // 9. Verificar que ambas cotizaciones (Q1 y Q2) continuan existiendo y reteniendo sus lineas y totales
        mockMvc.perform(get("/api/v1/quotes/" + q1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(354.00))
            .andExpect(jsonPath("$.items[0].name").value("Cloud Hosting"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(150.00));

        mockMvc.perform(get("/api/v1/quotes/" + q2Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1062.00))
            .andExpect(jsonPath("$.items[0].name").value("Cloud Hosting V2 Enterprise"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(450.00));
    }
}
