package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        quoteRepository.deleteAll();
        catalogItemRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    @Test
    @DisplayName("POST /api/v1/quotes creates quote with exact fractional calculations and tax")
    void createQuote_exactCalculations_success() throws Exception {
        // Create catalog item in USD for user 42
        String catResponse = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest(
                    "SRV-DEV", "Desarrollo Cloud", "Horas cloud", "USD", new BigDecimal("100.00"), true
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long catId = objectMapper.readTree(catResponse).get("id").asLong();

        // Line 1: Manual item, 2.50 qty * 150.33 price = 375.825 -> 375.83
        // Line 2: Catalog item, 1.50 qty * 100.00 price = 150.00
        // Subtotal = 375.83 + 150.00 = 525.83
        // Tax (18%) = 525.83 * 0.18 = 94.6494 -> 94.65
        // Total = 525.83 + 94.65 = 620.48
        QuoteCreateRequest request = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotización formal de servicios",
            List.of(
                new QuoteItemRequest(null, "MAN-01", "Concepto Manual", "Detalle", new BigDecimal("2.50"), new BigDecimal("150.33")),
                new QuoteItemRequest(catId, null, null, null, new BigDecimal("1.50"), null)
            )
        );

        mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.leadId").value(105))
            .andExpect(jsonPath("$.ownerId").value("42"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.subtotal").value(525.83))
            .andExpect(jsonPath("$.taxRate").value(0.18))
            .andExpect(jsonPath("$.tax").value(94.65))
            .andExpect(jsonPath("$.total").value(620.48))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].subtotal").value(375.83))
            .andExpect(jsonPath("$.items[1].name").value("Desarrollo Cloud"))
            .andExpect(jsonPath("$.items[1].subtotal").value(150.00));
    }

    @Test
    @DisplayName("POST /api/v1/quotes verifies RoundingMode.HALF_UP edge cases")
    void createQuote_roundingHalfUpEdgeCase() throws Exception {
        // 0.33 qty * 10.15 price = 3.3495 -> 3.35
        // taxRate = 0.1000 (10%) -> 3.35 * 0.10 = 0.335 -> 0.34
        // total = 3.35 + 0.34 = 3.69
        QuoteCreateRequest request = new QuoteCreateRequest(
            200L,
            "PEN",
            new BigDecimal("0.1000"),
            "Prueba de redondeo exacto",
            List.of(
                new QuoteItemRequest(null, "EDGE-01", "Item borde", "", new BigDecimal("0.33"), new BigDecimal("10.15"))
            )
        );

        mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(3.35))
            .andExpect(jsonPath("$.tax").value(0.34))
            .andExpect(jsonPath("$.total").value(3.69));
    }

    @Test
    @DisplayName("POST /api/v1/quotes rejects item currency mismatch against quote currency with 400")
    void createQuote_rejectsCurrencyMismatch() throws Exception {
        // Catalog item in PEN
        String catResponse = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest(
                    "SRV-PEN", "Servicio en Soles", "Desc", "PEN", new BigDecimal("50.00"), true
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long penCatId = objectMapper.readTree(catResponse).get("id").asLong();

        // Attempt to create quote in USD referencing the PEN catalog item
        QuoteCreateRequest request = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Mezcla de monedas no permitida",
            List.of(
                new QuoteItemRequest(penCatId, null, null, null, BigDecimal.ONE, null)
            )
        );

        mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/quotes/{id} isolates quotes by owner, returns 404 for foreign quote")
    void quote_ownerIsolation_returns404() throws Exception {
        QuoteCreateRequest request = new QuoteCreateRequest(
            301L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotizacion privada de user 99",
            List.of(
                new QuoteItemRequest(null, "SEC-01", "Item Secreto", "", BigDecimal.ONE, new BigDecimal("999.00"))
            )
        );

        String responseStr = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long quoteId99 = objectMapper.readTree(responseStr).get("id").asLong();

        // User 42 tries to view User 99's quote -> 404
        mockMvc.perform(get("/api/v1/quotes/" + quoteId99)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // User 99 views own quote -> 200 OK
        mockMvc.perform(get("/api/v1/quotes/" + quoteId99)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(quoteId99))
            .andExpect(jsonPath("$.ownerId").value("99"));
    }

    @Test
    @DisplayName("GET /api/v1/quotes lists paginated quotes with filter by leadId")
    void listQuotes_withFilters_success() throws Exception {
        // User 42 creates 2 quotes for lead 101 and 1 for lead 102
        QuoteCreateRequest q1 = new QuoteCreateRequest(101L, "USD", null, "Q1", List.of());
        QuoteCreateRequest q2 = new QuoteCreateRequest(101L, "USD", null, "Q2", List.of());
        QuoteCreateRequest q3 = new QuoteCreateRequest(102L, "PEN", null, "Q3", List.of());

        for (QuoteCreateRequest q : List.of(q1, q2, q3)) {
            mockMvc.perform(post("/api/v1/quotes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(q)))
                .andExpect(status().isCreated());
        }

        // List with leadId=101 filter -> 2 quotes
        mockMvc.perform(get("/api/v1/quotes?leadId=101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2));

        // List all quotes for user 42 -> 3 quotes
        mockMvc.perform(get("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/quotes/{id} updates quote in DRAFT and recalculates totals")
    void updateQuote_inDraft_recalculatesTotals() throws Exception {
        QuoteCreateRequest create = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Original notes",
            List.of(
                new QuoteItemRequest(null, "ITM-1", "Servicio", "", new BigDecimal("1.00"), new BigDecimal("100.00"))
            )
        );

        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Update taxRate to 0.1000 and notes
        QuoteUpdateRequest update = new QuoteUpdateRequest(new BigDecimal("0.1000"), "Notas actualizadas");

        mockMvc.perform(put("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value("Notas actualizadas"))
            .andExpect(jsonPath("$.taxRate").value(0.10))
            .andExpect(jsonPath("$.subtotal").value(100.00))
            .andExpect(jsonPath("$.tax").value(10.00))
            .andExpect(jsonPath("$.total").value(110.00));
    }

    @Test
    @DisplayName("PUT /api/v1/quotes/{id} rejects modification if quote is not in DRAFT")
    void updateQuote_notDraft_returns400() throws Exception {
        QuoteCreateRequest create = new QuoteCreateRequest(105L, "USD", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Alter status directly in DB to SENT
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.SENT);
        quoteRepository.save(quote);

        // Try to update
        QuoteUpdateRequest update = new QuoteUpdateRequest(new BigDecimal("0.1500"), "Cambio");
        mockMvc.perform(put("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/quotes/{id} deletes draft quote successfully")
    void deleteQuote_inDraft_success() throws Exception {
        QuoteCreateRequest create = new QuoteCreateRequest(105L, "USD", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        mockMvc.perform(delete("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST and DELETE /api/v1/quotes/{id}/items dynamically manages lines and updates totals")
    void addAndRemoveQuoteItems_recalculatesTotals() throws Exception {
        // Create initial quote with 1 item (100.00)
        QuoteCreateRequest create = new QuoteCreateRequest(
            105L,
            "PEN",
            new BigDecimal("0.1800"),
            "Cotizacion con lineas dinamicas",
            List.of(
                new QuoteItemRequest(null, "L1", "Linea inicial", "", BigDecimal.ONE, new BigDecimal("100.00"))
            )
        );

        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Add line: 2 units * 50.00 = 100.00
        // New subtotal: 200.00, Tax: 36.00, Total: 236.00
        QuoteItemRequest newItem = new QuoteItemRequest(null, "L2", "Segunda linea", "", new BigDecimal("2.00"), new BigDecimal("50.00"));
        String addRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newItem)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.subtotal").value(200.00))
            .andExpect(jsonPath("$.tax").value(36.00))
            .andExpect(jsonPath("$.total").value(236.00))
            .andReturn().getResponse().getContentAsString();

        Long addedItemId = objectMapper.readTree(addRes).get("items").get(1).get("id").asLong();

        // Remove the newly added line
        // Subtotal should return to 100.00, Tax: 18.00, Total: 118.00
        mockMvc.perform(delete("/api/v1/quotes/" + quoteId + "/items/" + addedItemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.subtotal").value(100.00))
            .andExpect(jsonPath("$.tax").value(18.00))
            .andExpect(jsonPath("$.total").value(118.00));
    }
}
