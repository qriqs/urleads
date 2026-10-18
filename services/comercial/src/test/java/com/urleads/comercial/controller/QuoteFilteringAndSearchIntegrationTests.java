package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteOutcomeRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteOutcomeReason;
import com.urleads.comercial.entity.QuoteStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteFilteringAndSearchIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        quoteRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    private Quote createQuoteDirectly(
        String ownerId,
        Long leadId,
        String currency,
        QuoteStatus status,
        QuoteOutcomeReason reason,
        String notes,
        Instant createdAt
    ) {
        Quote quote = new Quote(leadId, ownerId, currency, new BigDecimal("0.1800"), notes);
        quote.setStatus(status);
        quote.setOutcomeReason(reason);
        if (createdAt != null) {
            quote.setCreatedAt(createdAt);
        }
        return quoteRepository.save(quote);
    }

    @Test
    @DisplayName("GET /api/v1/quotes strictly isolates quotes by ownerId")
    void listQuotes_ownerScopingIsolation() throws Exception {
        createQuoteDirectly("42", 100L, "USD", QuoteStatus.DRAFT, null, "Cotización Sebas 1", Instant.now());
        createQuoteDirectly("42", 101L, "USD", QuoteStatus.DRAFT, null, "Cotización Sebas 2", Instant.now());
        createQuoteDirectly("99", 100L, "USD", QuoteStatus.DRAFT, null, "Cotización Ajena", Instant.now());

        mockMvc.perform(get("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(2)))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].ownerId", equalTo("42")))
            .andExpect(jsonPath("$.content[1].ownerId", equalTo("42")));

        mockMvc.perform(get("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].ownerId", equalTo("99")));
    }

    @Test
    @DisplayName("GET /api/v1/quotes filters by leadId, status, currency, and outcomeReason")
    void listQuotes_filterBySingleCriteria() throws Exception {
        createQuoteDirectly("42", 101L, "USD", QuoteStatus.DRAFT, null, "Q1", Instant.now());
        createQuoteDirectly("42", 102L, "PEN", QuoteStatus.SENT, null, "Q2", Instant.now());
        createQuoteDirectly("42", 103L, "USD", QuoteStatus.ACCEPTED, null, "Q3", Instant.now());
        createQuoteDirectly("42", 104L, "USD", QuoteStatus.REJECTED, QuoteOutcomeReason.PRECIO, "Q4", Instant.now());
        createQuoteDirectly("42", 105L, "PEN", QuoteStatus.REJECTED, QuoteOutcomeReason.COMPETENCIA, "Q5", Instant.now());

        // Filter by currency (case-insensitive)
        mockMvc.perform(get("/api/v1/quotes?currency=pen")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(2)));

        // Filter by status
        mockMvc.perform(get("/api/v1/quotes?status=REJECTED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(2)));

        // Filter by outcomeReason
        mockMvc.perform(get("/api/v1/quotes?outcomeReason=PRECIO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].outcomeReason", equalTo("PRECIO")));

        // Filter by leadId
        mockMvc.perform(get("/api/v1/quotes?leadId=103")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].leadId", equalTo(103)))
            .andExpect(jsonPath("$.content[0].status", equalTo("ACCEPTED")));
    }

    @Test
    @DisplayName("GET /api/v1/quotes combines multiple filters simultaneously")
    void listQuotes_combinedFilters() throws Exception {
        createQuoteDirectly("42", 200L, "USD", QuoteStatus.DRAFT, null, "Draft USD", Instant.now());
        createQuoteDirectly("42", 200L, "PEN", QuoteStatus.DRAFT, null, "Draft PEN", Instant.now());
        createQuoteDirectly("42", 200L, "USD", QuoteStatus.SENT, null, "Sent USD", Instant.now());
        createQuoteDirectly("42", 201L, "USD", QuoteStatus.DRAFT, null, "Other lead Draft USD", Instant.now());

        // leadId=200 AND status=DRAFT AND currency=USD -> exactly 1
        mockMvc.perform(get("/api/v1/quotes?leadId=200&status=DRAFT&currency=USD")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].notes", equalTo("Draft USD")));
    }

    @Test
    @DisplayName("GET /api/v1/quotes filters by date range (dateFrom and dateTo)")
    void listQuotes_dateRangeFiltering() throws Exception {
        Instant baseTime = Instant.parse("2026-10-15T12:00:00Z");

        createQuoteDirectly("42", 301L, "USD", QuoteStatus.DRAFT, null, "Oct 10 Quote", baseTime.minus(5, ChronoUnit.DAYS));
        createQuoteDirectly("42", 302L, "USD", QuoteStatus.DRAFT, null, "Oct 15 Quote", baseTime);
        createQuoteDirectly("42", 303L, "USD", QuoteStatus.DRAFT, null, "Oct 18 Quote", baseTime.plus(3, ChronoUnit.DAYS));
        createQuoteDirectly("42", 304L, "USD", QuoteStatus.DRAFT, null, "Oct 25 Quote", baseTime.plus(10, ChronoUnit.DAYS));

        // Date range 2026-10-14 to 2026-10-19 (calendar dates)
        mockMvc.perform(get("/api/v1/quotes?dateFrom=2026-10-14&dateTo=2026-10-19")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(2)));

        // Range using ISO-8601 timestamps and aliases 'from' and 'to'
        mockMvc.perform(get("/api/v1/quotes?from=2026-10-15T12:00:00Z&to=2026-10-25T12:00:00Z")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(3)));

        // Invalid date range: dateFrom is after dateTo -> 400 Bad Request
        mockMvc.perform(get("/api/v1/quotes?dateFrom=2026-10-20&dateTo=2026-10-10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));

        // Malformed date -> 400 Bad Request
        mockMvc.perform(get("/api/v1/quotes?dateFrom=not-a-date")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));
    }

    @Test
    @DisplayName("GET /api/v1/quotes performs text search 'q' across notes, items code/name/description, and IDs")
    void listQuotes_searchKeyword() throws Exception {
        // Create quote with lines via POST endpoint
        QuoteCreateRequest req1 = new QuoteCreateRequest(
            401L,
            "USD",
            new BigDecimal("0.1800"),
            "Implementación de arquitectura Microservicios",
            List.of(
                new QuoteItemRequest(null, "SRV-ARCH", "Consultoría Arquitectura", "Diseño distribuido", BigDecimal.ONE, new BigDecimal("1500.00"))
            )
        );

        QuoteCreateRequest req2 = new QuoteCreateRequest(
            402L,
            "PEN",
            new BigDecimal("0.1800"),
            "Desarrollo Frontend React con Vite",
            List.of(
                new QuoteItemRequest(null, "DEV-REACT", "Componentes UI", "Pantallas y dashboards", new BigDecimal("2.00"), new BigDecimal("450.00"))
            )
        );

        mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated());

        // Search by notes keyword ("microservicios")
        mockMvc.perform(get("/api/v1/quotes?q=microservicios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].leadId", equalTo(401)));

        // Search by item code ("DEV-REACT")
        mockMvc.perform(get("/api/v1/quotes?q=DEV-REACT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].leadId", equalTo(402)));

        // Search by item description ("distribuido")
        mockMvc.perform(get("/api/v1/quotes?search=distribuido")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].leadId", equalTo(401)));

        // Search with no matches
        mockMvc.perform(get("/api/v1/quotes?q=NON_EXISTENT_KEYWORD")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", equalTo(0)))
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/quotes guarantees deterministic pagination with stable ordering (createdAt DESC, id DESC)")
    void listQuotes_deterministicPaginationAndOrder() throws Exception {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        // Create 5 quotes with explicitly increasing timestamps
        Quote q1 = createQuoteDirectly("42", 501L, "USD", QuoteStatus.DRAFT, null, "Item 1", t0.plusSeconds(10));
        Quote q2 = createQuoteDirectly("42", 502L, "USD", QuoteStatus.DRAFT, null, "Item 2", t0.plusSeconds(20));
        Quote q3 = createQuoteDirectly("42", 503L, "USD", QuoteStatus.DRAFT, null, "Item 3", t0.plusSeconds(30));
        Quote q4 = createQuoteDirectly("42", 504L, "USD", QuoteStatus.DRAFT, null, "Item 4", t0.plusSeconds(40));
        Quote q5 = createQuoteDirectly("42", 505L, "USD", QuoteStatus.DRAFT, null, "Item 5", t0.plusSeconds(50));

        // Expected order DESC: q5, q4, q3, q2, q1

        // Page 0, size 2 -> q5, q4
        mockMvc.perform(get("/api/v1/quotes?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", equalTo(0)))
            .andExpect(jsonPath("$.size", equalTo(2)))
            .andExpect(jsonPath("$.totalElements", equalTo(5)))
            .andExpect(jsonPath("$.totalPages", equalTo(3)))
            .andExpect(jsonPath("$.last", equalTo(false)))
            .andExpect(jsonPath("$.content[0].id", equalTo(q5.getId().intValue())))
            .andExpect(jsonPath("$.content[1].id", equalTo(q4.getId().intValue())));

        // Page 1, size 2 -> q3, q2
        mockMvc.perform(get("/api/v1/quotes?page=1&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", equalTo(1)))
            .andExpect(jsonPath("$.size", equalTo(2)))
            .andExpect(jsonPath("$.totalElements", equalTo(5)))
            .andExpect(jsonPath("$.totalPages", equalTo(3)))
            .andExpect(jsonPath("$.last", equalTo(false)))
            .andExpect(jsonPath("$.content[0].id", equalTo(q3.getId().intValue())))
            .andExpect(jsonPath("$.content[1].id", equalTo(q2.getId().intValue())));

        // Page 2, size 2 -> q1 (last page)
        mockMvc.perform(get("/api/v1/quotes?page=2&size=2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", equalTo(2)))
            .andExpect(jsonPath("$.size", equalTo(2)))
            .andExpect(jsonPath("$.totalElements", equalTo(5)))
            .andExpect(jsonPath("$.totalPages", equalTo(3)))
            .andExpect(jsonPath("$.last", equalTo(true)))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].id", equalTo(q1.getId().intValue())));

        // Invalid pagination arguments (page < 0 or size <= 0) -> 400 Bad Request
        mockMvc.perform(get("/api/v1/quotes?page=-1&size=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));

        mockMvc.perform(get("/api/v1/quotes?page=0&size=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));
    }
}
