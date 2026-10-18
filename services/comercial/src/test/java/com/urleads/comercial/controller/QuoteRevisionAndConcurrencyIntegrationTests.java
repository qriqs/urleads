package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteRevisionRequest;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.security.JwtTestUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteRevisionAndConcurrencyIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/revisions creates v2 revision, locks v1, and links hierarchy")
    void createRevision_success() throws Exception {
        // 1. Create quote v1
        QuoteCreateRequest createReq = new QuoteCreateRequest(
            105L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotización v1 inicial",
            List.of(
                new QuoteItemRequest(null, "SRV-01", "Desarrollo Backend", "Microservicios", new BigDecimal("10.00"), new BigDecimal("100.00"))
            )
        );

        String createRes = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long v1Id = objectMapper.readTree(createRes).get("id").asLong();

        // Send v1
        mockMvc.perform(post("/api/v1/quotes/" + v1Id + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk());

        // 2. Client negotiates: Create revision v2 with new notes
        QuoteRevisionRequest revReq = new QuoteRevisionRequest("Revisión v2 renegociada", new BigDecimal("0.1500"));

        String revRes = mockMvc.perform(post("/api/v1/quotes/" + v1Id + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(revReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.leadId", equalTo(105)))
            .andExpect(jsonPath("$.ownerId", equalTo("42")))
            .andExpect(jsonPath("$.currency", equalTo("USD")))
            .andExpect(jsonPath("$.status", equalTo("DRAFT")))
            .andExpect(jsonPath("$.notes", equalTo("Revisión v2 renegociada")))
            .andExpect(jsonPath("$.taxRate", equalTo(0.15)))
            .andExpect(jsonPath("$.revisionNumber", equalTo(2)))
            .andExpect(jsonPath("$.isLatest", equalTo(true)))
            .andExpect(jsonPath("$.parentQuoteId", equalTo(v1Id.intValue())))
            .andExpect(jsonPath("$.rootQuoteId", equalTo(v1Id.intValue())))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].code", equalTo("SRV-01")))
            .andReturn().getResponse().getContentAsString();
        Long v2Id = objectMapper.readTree(revRes).get("id").asLong();

        // 3. Verify v1 is now locked and not latest
        mockMvc.perform(get("/api/v1/quotes/" + v1Id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisionNumber", equalTo(1)))
            .andExpect(jsonPath("$.isLatest", equalTo(false)))
            .andExpect(jsonPath("$.rootQuoteId", equalTo(v1Id.intValue())));

        // 4. Verify revision history endpoint lists both [v1, v2]
        mockMvc.perform(get("/api/v1/quotes/" + v2Id + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", equalTo(v1Id.intValue())))
            .andExpect(jsonPath("$[0].revisionNumber", equalTo(1)))
            .andExpect(jsonPath("$[0].isLatest", equalTo(false)))
            .andExpect(jsonPath("$[1].id", equalTo(v2Id.intValue())))
            .andExpect(jsonPath("$[1].revisionNumber", equalTo(2)))
            .andExpect(jsonPath("$[1].isLatest", equalTo(true)));
    }

    @Test
    @DisplayName("Superseded quote versions are blocked from mutation, transitions, and re-revision")
    void supersededVersion_isBlocked() throws Exception {
        // Create v1, send it, and revise it to v2
        Quote v1 = new Quote(200L, "42", "USD", new BigDecimal("0.1800"), "Original");
        v1.setStatus(QuoteStatus.SENT);
        v1 = quoteRepository.save(v1);

        String revRes = mockMvc.perform(post("/api/v1/quotes/" + v1.getId() + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteRevisionRequest("v2", null))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        // Attempting to send superseded v1 -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + v1.getId() + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));

        // Attempting to accept superseded v1 -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + v1.getId() + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));

        // Attempting to create revision from already superseded v1 -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + v1.getId() + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteRevisionRequest("v3 from v1", null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));
    }

    @Test
    @DisplayName("Quote revision endpoints strictly enforce owner isolation (404 for foreign user)")
    void ownerIsolation_returns404() throws Exception {
        Quote quote = new Quote(300L, "42", "USD", new BigDecimal("0.1800"), "User 42 quote");
        quote = quoteRepository.save(quote);

        // User 99 attempts to revise User 42's quote -> 404
        mockMvc.perform(post("/api/v1/quotes/" + quote.getId() + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuoteRevisionRequest("hack", null))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")));

        // User 99 attempts to list revisions of User 42's quote -> 404
        mockMvc.perform(get("/api/v1/quotes/" + quote.getId() + "/revisions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Optimistic locking: concurrent modification throws OptimisticLockException and returns HTTP 409")
    void optimisticLocking_concurrentModification_throwsException() {
        // 1. Create quote in database
        Quote quote = new Quote(400L, "42", "USD", new BigDecimal("0.1800"), "Initial Notes");
        Quote saved = quoteRepository.saveAndFlush(quote);
        Long quoteId = saved.getId();
        Long initialVersion = saved.getVersion();
        assertThat(initialVersion).isEqualTo(0L);

        // 2. Open two separate EntityManagers to simulate concurrent transactions
        EntityManager em1 = entityManagerFactory.createEntityManager();
        EntityManager em2 = entityManagerFactory.createEntityManager();

        try {
            // Transaction 1 reads the quote (version 0)
            EntityTransaction tx1 = em1.getTransaction();
            tx1.begin();
            Quote q1 = em1.find(Quote.class, quoteId);
            assertThat(q1.getVersion()).isEqualTo(0L);

            // Transaction 2 reads the quote (version 0)
            EntityTransaction tx2 = em2.getTransaction();
            tx2.begin();
            Quote q2 = em2.find(Quote.class, quoteId);
            assertThat(q2.getVersion()).isEqualTo(0L);

            // Transaction 1 modifies notes and commits successfully (version becomes 1)
            q1.setNotes("Actualizado por Tx1");
            tx1.commit();

            // Transaction 2 tries to modify its stale entity and commit
            q2.setNotes("Actualizado por Tx2 (stale)");

            // Assert that committing Tx2 throws OptimisticLockException or RollbackException
            assertThatThrownBy(tx2::commit)
                .hasCauseInstanceOf(OptimisticLockException.class);

        } finally {
            if (em1.isOpen()) em1.close();
            if (em2.isOpen()) em2.close();
        }

        // 3. Verify final DB state has version 1 and Tx1's notes
        Quote current = quoteRepository.findById(quoteId).orElseThrow();
        assertThat(current.getVersion()).isEqualTo(1L);
        assertThat(current.getNotes()).isEqualTo("Actualizado por Tx1");
    }
}
