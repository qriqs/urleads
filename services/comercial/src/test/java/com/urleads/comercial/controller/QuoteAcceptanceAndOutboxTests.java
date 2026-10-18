package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.entity.OutboxStatus;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.OutboxEventRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.security.JwtTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteAcceptanceAndOutboxTests {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String user42Token;
    private String user99Token;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("crm.service.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        quoteRepository.deleteAll();
        catalogItemRepository.deleteAll();
        wireMockServer.resetAll();

        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access crm.leads.read");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access crm.leads.read");
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/accept transitions to ACCEPTED, creates outbox event without PII and publishes to RabbitMQ")
    void acceptQuote_success_publishesOutboxEventWithoutPii() throws Exception {
        // Mock CRM endpoint confirming lead 105 exists
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
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

        // Create quote for lead 105
        QuoteCreateRequest create = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Nota estrictamente confidencial con datos privados del cliente",
            List.of(
                new QuoteItemRequest(null, "SRV-01", "Consultoria VIP", "Detalles", new BigDecimal("2.00"), new BigDecimal("500.00"))
            )
        );

        String createRes = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long quoteId = objectMapper.readTree(createRes).get("id").asLong();

        // Accept quote
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(quoteId))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.total").value(1180.00));

        // 1. Verify quote in database has ACCEPTED status
        Quote acceptedQuote = quoteRepository.findById(quoteId).orElseThrow();
        assertThat(acceptedQuote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);

        // 2. Verify outbox event persisted atomically
        List<OutboxEvent> outboxList = outboxEventRepository.findByOwnerId("42");
        assertThat(outboxList).hasSize(1);
        OutboxEvent event = outboxList.get(0);
        assertThat(event.getEventType()).isEqualTo("quotation.accepted.v1");
        assertThat(event.getVersion()).isEqualTo(1);
        assertThat(event.getOwnerId()).isEqualTo("42");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);

        // 3. Verify event envelope JSON contains NO PII
        JsonNode payloadNode = objectMapper.readTree(event.getPayload());
        assertThat(payloadNode.get("eventId")).isNotNull();
        assertThat(payloadNode.get("eventType").asText()).isEqualTo("quotation.accepted.v1");
        assertThat(payloadNode.get("version").asInt()).isEqualTo(1);
        assertThat(payloadNode.get("ownerId").asText()).isEqualTo("42");

        JsonNode dataNode = payloadNode.get("data");
        assertThat(dataNode.get("quoteId").asLong()).isEqualTo(quoteId);
        assertThat(dataNode.get("leadId").asLong()).isEqualTo(105L);
        assertThat(dataNode.get("currency").asText()).isEqualTo("USD");
        assertThat(dataNode.get("total").asDouble()).isEqualTo(1180.00);

        // Explicit check for absence of PII
        assertThat(payloadNode.has("notes")).isFalse();
        assertThat(dataNode.has("notes")).isFalse();
        assertThat(payloadNode.has("email")).isFalse();
        assertThat(payloadNode.has("phone")).isFalse();
        assertThat(payloadNode.has("name")).isFalse();
        assertThat(event.getPayload()).doesNotContain("Nota estrictamente confidencial");

        // 4. Verify RabbitMQ dispatch with canonical routing key and exchange
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(RabbitMqConfig.EVENTS_EXCHANGE),
            eq(RabbitMqConfig.QUOTATION_ACCEPTED_ROUTING_KEY),
            anyString()
        );
    }

    @Test
    @DisplayName("Idempotency: double acceptance returns 200 OK without creating duplicate outbox events")
    void acceptQuote_idempotency_noDuplicateEvents() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": 105, \"stage\": \"EN_SEGUIMIENTO\"}")));

        QuoteCreateRequest create = new QuoteCreateRequest(105L, "PEN", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // First acceptance
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Second acceptance (idempotent call)
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Exactly 1 outbox event must exist in database
        List<OutboxEvent> outboxList = outboxEventRepository.findByOwnerId("42");
        assertThat(outboxList).hasSize(1);

        // RabbitMQ template was only called once
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(RabbitMqConfig.EVENTS_EXCHANGE),
            eq(RabbitMqConfig.QUOTATION_ACCEPTED_ROUTING_KEY),
            anyString()
        );
    }

    @Test
    @DisplayName("Acceptance fails atomically if lead does not exist in CRM (404), rollback maintains DRAFT and no outbox")
    void acceptQuote_leadNotFoundInCrm_atomicRollback() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/999"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\": 404, \"code\": \"NOT_FOUND\"}")));

        QuoteCreateRequest create = new QuoteCreateRequest(999L, "USD", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Attempt accept: CRM returns 404
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Quote remains in DRAFT
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);

        // Zero outbox events created
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Acceptance fails cleanly with 503 if CRM is down, rollback maintains DRAFT and no outbox")
    void acceptQuote_crmDown_returns503_atomicRollback() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse().withStatus(500)));

        QuoteCreateRequest create = new QuoteCreateRequest(105L, "USD", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Attempt accept: CRM returns 500 -> 503 clean
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CRM_UNAVAILABLE"));

        // Quote remains DRAFT and no outbox event
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Acceptance rejects quotes in REJECTED status with 400 INVALID_STATE")
    void acceptQuote_rejectedQuote_returns400() throws Exception {
        QuoteCreateRequest create = new QuoteCreateRequest(105L, "USD", null, "", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // Set status to REJECTED directly in database
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.REJECTED);
        quoteRepository.save(quote);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Acceptance isolates by owner: user 42 cannot accept user 99's quote (returns 404)")
    void acceptQuote_ownerIsolation_returns404() throws Exception {
        QuoteCreateRequest create = new QuoteCreateRequest(105L, "USD", null, "Privada 99", List.of());
        String res = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(res).get("id").asLong();

        // User 42 tries to accept User 99's quote -> 404
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(outboxEventRepository.count()).isZero();
    }
}
