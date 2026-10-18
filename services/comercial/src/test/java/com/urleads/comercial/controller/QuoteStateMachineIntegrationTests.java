package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.client.dto.LeadInternalResponse;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteStateMachineIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

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
        quoteRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    private Long createDraftQuote(String ownerId, String token) throws Exception {
        QuoteCreateRequest request = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotización inicial en borrador",
            List.of(
                new QuoteItemRequest(null, "SRV-01", "Servicio Base", "Detalle", BigDecimal.ONE, new BigDecimal("250.00"))
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
    @DisplayName("POST /api/v1/quotes/{id}/send transitions quote from DRAFT to SENT")
    void sendQuote_fromDraft_success() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(quoteId))
            .andExpect(jsonPath("$.status").value("SENT"));

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.SENT, quote.getStatus());
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/send is idempotent when already SENT")
    void sendQuote_idempotent() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // First send
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        // Second send (idempotent)
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.SENT, quote.getStatus());
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/send rejects sending quote in final state (ACCEPTED or REJECTED)")
    void sendQuote_inFinalState_returns400() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // Mark as ACCEPTED
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Mark as REJECTED
        quote.setStatus(QuoteStatus.REJECTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/reject transitions quote to REJECTED from DRAFT or SENT")
    void rejectQuote_success() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // Transition DRAFT -> SENT
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        // Transition SENT -> REJECTED
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.REJECTED, quote.getStatus());

        // Idempotency: second reject returns 200 OK
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/reject rejects rejecting an already ACCEPTED quote")
    void rejectQuote_whenAccepted_returns400() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quoteRepository.save(quote);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/accept transitions SENT quote to ACCEPTED and dispatches outbox event")
    void acceptQuote_fromSent_success() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // Transition DRAFT -> SENT
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        // Mock CRM response
        Mockito.when(crmLeadService.getLeadById(105L))
            .thenReturn(new LeadInternalResponse(105L, "EN_SEGUIMIENTO", LocalDate.of(2026, 10, 25)));

        // Transition SENT -> ACCEPTED
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertEquals(QuoteStatus.ACCEPTED, quote.getStatus());

        // Verify outbox published
        assertEquals(1, outboxEventRepository.count());
        Mockito.verify(rabbitTemplate, Mockito.times(1))
            .convertAndSend(eq(RabbitMqConfig.EVENTS_EXCHANGE), eq(RabbitMqConfig.QUOTATION_ACCEPTED_ROUTING_KEY), any(Object.class));
    }

    @Test
    @DisplayName("PUT /api/v1/quotes/{id} is strictly blocked on ACCEPTED and REJECTED states")
    void updateQuote_blockedOnFinalStates() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);
        QuoteUpdateRequest updateReq = new QuoteUpdateRequest(new BigDecimal("0.1000"), "Intento de edición");

        // 1. Blocked on ACCEPTED
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(put("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 2. Blocked on REJECTED
        quote.setStatus(QuoteStatus.REJECTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(put("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/quotes/{id} is strictly blocked on ACCEPTED and REJECTED states")
    void deleteQuote_blockedOnFinalStates() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // 1. Blocked on ACCEPTED
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(delete("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 2. Blocked on REJECTED
        quote.setStatus(QuoteStatus.REJECTED);
        quote = quoteRepository.save(quote);

        mockMvc.perform(delete("/api/v1/quotes/" + quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Adding or removing items is strictly blocked on ACCEPTED and REJECTED states")
    void itemsManagement_blockedOnFinalStates() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);
        Long existingItemId = quoteItemRepository.findAll().get(0).getId();

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();

        QuoteItemRequest newItemReq = new QuoteItemRequest(null, "NEW-01", "Nuevo", "", BigDecimal.ONE, new BigDecimal("50.00"));

        // Set to ACCEPTED
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote = quoteRepository.save(quote);

        // Attempt add item
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newItemReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Attempt remove item
        mockMvc.perform(delete("/api/v1/quotes/" + quoteId + "/items/" + existingItemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Set to REJECTED
        quote.setStatus(QuoteStatus.REJECTED);
        quote = quoteRepository.save(quote);

        // Attempt add item
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newItemReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Attempt remove item
        mockMvc.perform(delete("/api/v1/quotes/" + quoteId + "/items/" + existingItemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Owner isolation on state transitions: User 99 cannot send or reject quote of User 42")
    void ownerIsolation_returns404() throws Exception {
        Long quoteId = createDraftQuote("42", user42Token);

        // User 99 attempts to send User 42's quote
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // User 99 attempts to reject User 42's quote
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
