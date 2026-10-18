package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteOutcomeRequest;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteOutcomeReason;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.QuoteItemRepository;
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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteOutcomeIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private QuoteItemRepository quoteItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        quoteItemRepository.deleteAll();
        quoteRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    private Long createDraftQuote(String token) throws Exception {
        QuoteCreateRequest request = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotización para evaluación de resultado",
            List.of(
                new QuoteItemRequest(null, "SRV-TEST", "Servicio Test", "Detalle", BigDecimal.ONE, new BigDecimal("300.00"))
            )
        );

        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(res).get("id").asLong();
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/reject records structured reason, notes and date")
    void rejectQuote_withStructuredOutcome_success() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        // Enviar a cliente primero
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk());

        Instant fixedDate = Instant.parse("2026-10-18T10:30:00Z");
        QuoteOutcomeRequest outcomeReq = new QuoteOutcomeRequest(
            QuoteOutcomeReason.PRECIO,
            "El cliente indico que el costo excede su partida presupuestaria en un 15%",
            fixedDate
        );

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(outcomeReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(quoteId))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.outcomeReason").value("PRECIO"))
            .andExpect(jsonPath("$.outcomeNotes").value("El cliente indico que el costo excede su partida presupuestaria en un 15%"))
            .andExpect(jsonPath("$.outcomeDate").value("2026-10-18T10:30:00Z"));

        // Comprobación directa en base de datos
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.REJECTED, quote.getStatus());
        assertEquals(QuoteOutcomeReason.PRECIO, quote.getOutcomeReason());
        assertEquals("El cliente indico que el costo excede su partida presupuestaria en un 15%", quote.getOutcomeNotes());
        assertEquals(fixedDate, quote.getOutcomeDate());
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/outcome records commercial outcome and closes quote as REJECTED")
    void recordOutcome_fromSentOrDraft_success() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        QuoteOutcomeRequest outcomeReq = new QuoteOutcomeRequest(
            QuoteOutcomeReason.COMPETENCIA,
            "El cliente eligio una propuesta alternativa de la competencia",
            null
        );

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(outcomeReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(quoteId))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.outcomeReason").value("COMPETENCIA"))
            .andExpect(jsonPath("$.outcomeNotes").value("El cliente eligio una propuesta alternativa de la competencia"))
            .andExpect(jsonPath("$.outcomeDate").isNotEmpty());

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.REJECTED, quote.getStatus());
        assertEquals(QuoteOutcomeReason.COMPETENCIA, quote.getOutcomeReason());
        assertNotNull(quote.getOutcomeDate());
    }

    @Test
    @DisplayName("Updating outcome on already REJECTED quote updates reason and notes idempotently")
    void updateOutcome_onAlreadyRejectedQuote_updatesValues() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        // Primer rechazo por PLAZOS
        QuoteOutcomeRequest firstReq = new QuoteOutcomeRequest(
            QuoteOutcomeReason.PLAZOS,
            "Plazos de entrega muy extensos",
            null
        );
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outcomeReason").value("PLAZOS"));

        // Modificación de cierre comercial a DESCARTE
        QuoteOutcomeRequest secondReq = new QuoteOutcomeRequest(
            QuoteOutcomeReason.DESCARTE,
            "Proyecto cancelado internamente por el cliente",
            null
        );
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.outcomeReason").value("DESCARTE"))
            .andExpect(jsonPath("$.outcomeNotes").value("Proyecto cancelado internamente por el cliente"));

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteOutcomeReason.DESCARTE, quote.getOutcomeReason());
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/outcome fails if reason is null")
    void recordOutcome_nullReason_returnsBadRequest() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        String invalidJson = "{\"reason\": null, \"notes\": \"Sin motivo\"}";

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Recording outcome or rejecting an ACCEPTED quote is blocked with 400 INVALID_STATE")
    void recordOutcome_whenAccepted_returns400() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        // Modificar a ACCEPTED
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quoteRepository.save(quote);

        QuoteOutcomeRequest req = new QuoteOutcomeRequest(
            QuoteOutcomeReason.OTRO,
            "Intento de rechazar una cotizacion aceptada",
            null
        );

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Quotes rejected with outcome remain strictly protected against editing or deletion")
    void rejectedWithOutcome_blockedForEditAndDelete() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        QuoteOutcomeRequest req = new QuoteOutcomeRequest(QuoteOutcomeReason.PRECIO, "Motivo precio", null);
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // Intento de edición
        QuoteUpdateRequest updateReq = new QuoteUpdateRequest(new BigDecimal("0.1000"), "Intento");
        mockMvc.perform(put("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Intento de eliminación
        mockMvc.perform(delete("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Owner isolation: User 99 cannot record outcome or view outcome of User 42")
    void ownerIsolation_returnsNotFound() throws Exception {
        Long quoteId = createDraftQuote(user42Token);

        QuoteOutcomeRequest req = new QuoteOutcomeRequest(QuoteOutcomeReason.COMPETENCIA, "Intento ajeno", null);

        // User 99 intenta registrar outcome en cotización de User 42
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // User 99 intenta consultar cotización de User 42
        mockMvc.perform(get("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
