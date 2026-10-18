package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.dto.InstantiateQuoteRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteTemplateRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteTemplate;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.repository.QuoteTemplateRepository;
import com.urleads.comercial.security.JwtTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteTemplateIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteTemplateRepository quoteTemplateRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        quoteRepository.deleteAll();
        quoteTemplateRepository.deleteAll();
        catalogItemRepository.deleteAll();

        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    @Test
    @DisplayName("POST /api/v1/quote-templates creates template with lines and calculates totals")
    void createTemplate_success() throws Exception {
        // Create catalog item in USD for user 42
        String catRes = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest(
                    "CAT-SRV", "Soporte Infraestructura", "Mantenimiento servidores", "USD", new BigDecimal("200.00"), true
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long catId = objectMapper.readTree(catRes).get("id").asLong();

        // Template with 1 catalog item + 1 manual item
        // Catalog: 2.00 qty * 200.00 = 400.00
        // Manual: 1.00 qty * 150.50 = 150.50
        // Subtotal = 550.50, Tax (18%) = 99.09, Total = 649.59
        QuoteTemplateRequest request = new QuoteTemplateRequest(
            "Plantilla Mantenimiento IT",
            "Servicios mensuales corporativos",
            "USD",
            new BigDecimal("0.1800"),
            "Condiciones de pago 30 días",
            List.of(
                new QuoteItemRequest(catId, null, null, null, new BigDecimal("2.00"), null),
                new QuoteItemRequest(null, "MAN-ONCALL", "Guardia 24/7", "Disponibilidad nocturna", BigDecimal.ONE, new BigDecimal("150.50"))
            )
        );

        mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.ownerId", equalTo("42")))
            .andExpect(jsonPath("$.name", equalTo("Plantilla Mantenimiento IT")))
            .andExpect(jsonPath("$.currency", equalTo("USD")))
            .andExpect(jsonPath("$.subtotal", equalTo(550.50)))
            .andExpect(jsonPath("$.tax", equalTo(99.09)))
            .andExpect(jsonPath("$.total", equalTo(649.59)))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].code", equalTo("CAT-SRV")))
            .andExpect(jsonPath("$.items[0].subtotal", equalTo(400.00)))
            .andExpect(jsonPath("$.items[1].code", equalTo("MAN-ONCALL")))
            .andExpect(jsonPath("$.items[1].subtotal", equalTo(150.50)));
    }

    @Test
    @DisplayName("POST /api/v1/quote-templates rejects duplicate template name with 409 Conflict")
    void createTemplate_duplicateName_conflict() throws Exception {
        QuoteTemplateRequest request = new QuoteTemplateRequest(
            "Plantilla Duplicada", "Desc", "USD", null, "Notas", List.of()
        );

        mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", equalTo("DUPLICATE_CODE")));
    }

    @Test
    @DisplayName("POST /api/v1/quote-templates rejects item currency mismatch with 400 Bad Request")
    void createTemplate_currencyMismatch_badRequest() throws Exception {
        // Create catalog item in PEN
        String catRes = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest(
                    "SRV-PEN", "Servicio en Soles", "Desc", "PEN", new BigDecimal("100.00"), true
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long penCatId = objectMapper.readTree(catRes).get("id").asLong();

        // Attempt template in USD with PEN catalog item
        QuoteTemplateRequest request = new QuoteTemplateRequest(
            "Plantilla Incompatible", "Desc", "USD", null, "Notas",
            List.of(new QuoteItemRequest(penCatId, null, null, null, BigDecimal.ONE, null))
        );

        mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));
    }

    @Test
    @DisplayName("GET /api/v1/quote-templates lists paginated templates with owner scoping")
    void listTemplates_ownerScopingAndPagination() throws Exception {
        // User 42 creates 3 templates
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/quote-templates")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                        "Plantilla " + i, "Desc", "USD", null, "Notas", List.of()
                    ))))
                .andExpect(status().isCreated());
        }

        // User 99 creates 1 template
        mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                    "Plantilla Ajena", "Desc", "USD", null, "Notas", List.of()
                ))))
            .andExpect(status().isCreated());

        // User 42 lists: exactly 3 templates
        mockMvc.perform(get("/api/v1/quote-templates?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", equalTo(0)))
            .andExpect(jsonPath("$.size", equalTo(2)))
            .andExpect(jsonPath("$.totalElements", equalTo(3)))
            .andExpect(jsonPath("$.totalPages", equalTo(2)))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].ownerId", equalTo("42")));

        // User 99 lists: exactly 1 template
        mockMvc.perform(get("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].name", equalTo("Plantilla Ajena")));
    }

    @Test
    @DisplayName("PUT and DELETE /api/v1/quote-templates/{id} updates and removes template")
    void updateAndDeleteTemplate() throws Exception {
        String res = mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                    "Original Template", "Original Desc", "PEN", null, "Notas", List.of()
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(res).get("id").asLong();

        // Update template
        mockMvc.perform(put("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                    "Updated Template", "Updated Desc", "PEN", new BigDecimal("0.1000"), "Nuevas notas",
                    List.of(new QuoteItemRequest(null, "NEW-01", "Nuevo Concepto", "Detalle", BigDecimal.ONE, new BigDecimal("100.00")))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", equalTo("Updated Template")))
            .andExpect(jsonPath("$.taxRate", equalTo(0.10)))
            .andExpect(jsonPath("$.items", hasSize(1)));

        // Delete template
        mockMvc.perform(delete("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNoContent());

        // Verify 404 after delete
        mockMvc.perform(get("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Quote template endpoints strictly isolate access by owner (returns 404 for foreign user)")
    void ownerIsolation_returns404() throws Exception {
        String res = mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                    "Privada User 42", "Desc", "USD", null, "Notas", List.of()
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(res).get("id").asLong();

        // User 99 attempts GET -> 404
        mockMvc.perform(get("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")));

        // User 99 attempts PUT -> 404
        mockMvc.perform(put("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest("Hack", "Desc", "USD", null, "", List.of()))))
            .andExpect(status().isNotFound());

        // User 99 attempts DELETE -> 404
        mockMvc.perform(delete("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound());

        // User 99 attempts instantiate -> 404
        mockMvc.perform(post("/api/v1/quote-templates/" + templateId + "/create-quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InstantiateQuoteRequest(901L, null, null))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/quote-templates/{id}/create-quote generates independent quote with full deep copy decoupling")
    void instantiateQuote_deepCopyAndTotalIndependence() throws Exception {
        // 1. Create a rich template
        QuoteTemplateRequest templateReq = new QuoteTemplateRequest(
            "Plantilla Consultoría Completa",
            "Incluye auditoría e informe",
            "USD",
            new BigDecimal("0.1800"),
            "Validez de la oferta 15 días calendario",
            List.of(
                new QuoteItemRequest(null, "SRV-AUDIT", "Auditoría de Seguridad", "Revisión OWASP", new BigDecimal("2.00"), new BigDecimal("300.00")),
                new QuoteItemRequest(null, "SRV-REPORT", "Informe Técnico", "Entrega PDF ejecutivo", BigDecimal.ONE, new BigDecimal("200.00"))
            )
        );

        String templateRes = mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateReq)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(templateRes).get("id").asLong();

        // 2. Instantiate Quote from template for lead 888
        InstantiateQuoteRequest instReq = new InstantiateQuoteRequest(888L, "Notas personalizadas para cliente VIP", new BigDecimal("0.1000"));

        String quoteRes = mockMvc.perform(post("/api/v1/quote-templates/" + templateId + "/create-quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(instReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.leadId", equalTo(888)))
            .andExpect(jsonPath("$.ownerId", equalTo("42")))
            .andExpect(jsonPath("$.currency", equalTo("USD")))
            .andExpect(jsonPath("$.status", equalTo("DRAFT")))
            .andExpect(jsonPath("$.notes", equalTo("Notas personalizadas para cliente VIP")))
            .andExpect(jsonPath("$.taxRate", equalTo(0.10)))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].code", equalTo("SRV-AUDIT")))
            .andExpect(jsonPath("$.items[0].subtotal", equalTo(600.00)))
            .andExpect(jsonPath("$.items[1].code", equalTo("SRV-REPORT")))
            .andExpect(jsonPath("$.items[1].subtotal", equalTo(200.00)))
            .andExpect(jsonPath("$.subtotal", equalTo(800.00)))
            .andExpect(jsonPath("$.tax", equalTo(80.00)))
            .andExpect(jsonPath("$.total", equalTo(880.00)))
            .andReturn().getResponse().getContentAsString();
        Long createdQuoteId = objectMapper.readTree(quoteRes).get("id").asLong();

        // 3. Test independence 1: Mutate created quote by removing a line and updating notes
        Long quoteItemId = objectMapper.readTree(quoteRes).get("items").get(0).get("id").asLong();
        mockMvc.perform(delete("/api/v1/quotes/" + createdQuoteId + "/items/" + quoteItemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)));

        // Verify template STILL HAS its original 2 items!
        mockMvc.perform(get("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.notes", equalTo("Validez de la oferta 15 días calendario")));

        // 4. Test independence 2: Mutate template (change name, clear lines)
        mockMvc.perform(put("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteTemplateRequest(
                    "Plantilla Vaciada", "Desc", "USD", null, "Nuevas notas", List.of()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        // Verify quote still retains its item!
        mockMvc.perform(get("/api/v1/quotes/" + createdQuoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].code", equalTo("SRV-REPORT")));

        // 5. Test independence 3: Delete template entirely
        mockMvc.perform(delete("/api/v1/quote-templates/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNoContent());

        // Verify created quote is STILL fully present and queryable!
        mockMvc.perform(get("/api/v1/quotes/" + createdQuoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(createdQuoteId.intValue())))
            .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/quotes/from-template/{templateId} creates quote from template via quote route")
    void instantiateQuote_viaQuoteRoute() throws Exception {
        QuoteTemplateRequest templateReq = new QuoteTemplateRequest(
            "Plantilla Ruta Cotizaciones", "Desc", "PEN", new BigDecimal("0.1800"), "Notas base",
            List.of(new QuoteItemRequest(null, "SRV-X", "Servicio X", "", BigDecimal.ONE, new BigDecimal("500.00")))
        );

        String templateRes = mockMvc.perform(post("/api/v1/quote-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateReq)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(templateRes).get("id").asLong();

        // Call via /api/v1/quotes/from-template/{templateId}
        mockMvc.perform(post("/api/v1/quotes/from-template/" + templateId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InstantiateQuoteRequest(777L, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.leadId", equalTo(777)))
            .andExpect(jsonPath("$.currency", equalTo("PEN")))
            .andExpect(jsonPath("$.notes", equalTo("Notas base")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].code", equalTo("SRV-X")));
    }
}
