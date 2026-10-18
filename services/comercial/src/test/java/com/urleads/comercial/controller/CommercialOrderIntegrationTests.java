package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.entity.CommercialOrder;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.CommercialOrderRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommercialOrderIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CommercialOrderRepository commercialOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        commercialOrderRepository.deleteAll();
        quoteRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    private Long createAndAcceptQuote(String ownerId, String token) throws Exception {
        QuoteCreateRequest request = new QuoteCreateRequest(
            201L,
            "USD",
            new BigDecimal("0.1800"),
            "Cotización para orden",
            List.of(
                new QuoteItemRequest(null, "SRV-ORD", "Implementación", "Detalle servicio", new BigDecimal("3.00"), new BigDecimal("150.00"))
            )
        );

        String quoteRes = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(quoteRes).get("id").asLong();

        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setStatus(QuoteStatus.ACCEPTED);
        quoteRepository.save(quote);

        return quoteId;
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/orders creates commercial order from ACCEPTED quote")
    void generateOrder_success() throws Exception {
        Long quoteId = createAndAcceptQuote("42", user42Token);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.orderNumber", startsWith("ORD-")))
            .andExpect(jsonPath("$.quoteId", equalTo(quoteId.intValue())))
            .andExpect(jsonPath("$.leadId", equalTo(201)))
            .andExpect(jsonPath("$.ownerId", equalTo("42")))
            .andExpect(jsonPath("$.currency", equalTo("USD")))
            .andExpect(jsonPath("$.status", equalTo("CONFIRMED")))
            .andExpect(jsonPath("$.subtotal", equalTo(450.0)))
            .andExpect(jsonPath("$.tax", equalTo(81.0)))
            .andExpect(jsonPath("$.total", equalTo(531.0)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].code", equalTo("SRV-ORD")))
            .andExpect(jsonPath("$.items[0].quantity", equalTo(3.0)))
            .andExpect(jsonPath("$.items[0].unitPrice", equalTo(150.0)))
            .andExpect(jsonPath("$.items[0].subtotal", equalTo(450.0)));

        assertThat(commercialOrderRepository.count()).isEqualTo(1);
        CommercialOrder orderInDb = commercialOrderRepository.findAll().get(0);
        assertThat(orderInDb.getQuoteId()).isEqualTo(quoteId);
        assertThat(orderInDb.getOwnerId()).isEqualTo("42");
    }

    @Test
    @DisplayName("POST /api/v1/quotes/{id}/orders is strictly idempotent upon repeated requests")
    void generateOrder_idempotent() throws Exception {
        Long quoteId = createAndAcceptQuote("42", user42Token);

        // 1. First order generation
        String firstRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long firstOrderId = objectMapper.readTree(firstRes).get("id").asLong();
        String firstOrderNumber = objectMapper.readTree(firstRes).get("orderNumber").asText();

        // 2. Second order generation (idempotent retry)
        String secondRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long secondOrderId = objectMapper.readTree(secondRes).get("id").asLong();
        String secondOrderNumber = objectMapper.readTree(secondRes).get("orderNumber").asText();

        // 3. Third order generation using singular alias /order
        String thirdRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long thirdOrderId = objectMapper.readTree(thirdRes).get("id").asLong();

        // Exactly the same order ID and number returned
        assertThat(secondOrderId).isEqualTo(firstOrderId);
        assertThat(thirdOrderId).isEqualTo(firstOrderId);
        assertThat(secondOrderNumber).isEqualTo(firstOrderNumber);

        // Crucial: exactly 1 order in the database table
        assertThat(commercialOrderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Generating order is strictly rejected with HTTP 400 when quote is not in ACCEPTED state")
    void generateOrder_rejectedOnNonAcceptedState() throws Exception {
        // Create draft quote
        QuoteCreateRequest request = new QuoteCreateRequest(
            301L,
            "USD",
            new BigDecimal("0.1800"),
            "Borrador",
            List.of(new QuoteItemRequest(null, "SRV-01", "Servicio", "", BigDecimal.ONE, new BigDecimal("100.00")))
        );

        String quoteRes = mockMvc.perform(post("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long quoteId = objectMapper.readTree(quoteRes).get("id").asLong();

        // 1. Attempt while DRAFT -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));

        // 2. Transition to SENT and attempt -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));

        // 3. Transition to REJECTED and attempt -> 400 INVALID_STATE
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));

        assertThat(commercialOrderRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Generating order is strictly rejected when quote is a superseded version (isLatest = false)")
    void generateOrder_rejectedOnSupersededQuote() throws Exception {
        Long quoteId = createAndAcceptQuote("42", user42Token);

        // Mark quote as superseded
        Quote quote = quoteRepository.findById(quoteId).orElseThrow();
        quote.setLatest(false);
        quoteRepository.save(quote);

        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("INVALID_STATE")));
    }

    @Test
    @DisplayName("Owner isolation: User 99 cannot generate or view User 42's commercial orders (404 Not Found)")
    void ownerIsolation_returns404() throws Exception {
        Long quoteId = createAndAcceptQuote("42", user42Token);

        // 1. User 99 attempts to generate order for User 42's quote -> 404
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")));

        // Generate order as User 42
        String orderRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(orderRes).get("id").asLong();

        // 2. User 99 attempts to query User 42's order by direct ID -> 404
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")));

        // 3. User 99 attempts to query order via quote ID -> 404
        mockMvc.perform(get("/api/v1/quotes/" + quoteId + "/order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET endpoints: /api/v1/quotes/{id}/order, /api/v1/orders/{id}, and /api/v1/orders pagination work accurately")
    void queryEndpoints_success() throws Exception {
        Long quoteId = createAndAcceptQuote("42", user42Token);

        // Generate order
        String orderRes = mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(orderRes).get("id").asLong();

        // Query by quote ID
        mockMvc.perform(get("/api/v1/quotes/" + quoteId + "/order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(orderId.intValue())))
            .andExpect(jsonPath("$.quoteId", equalTo(quoteId.intValue())));

        // Query by order ID
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(orderId.intValue())))
            .andExpect(jsonPath("$.orderNumber", startsWith("ORD-")));

        // Query paginated orders list
        mockMvc.perform(get("/api/v1/orders?page=0&size=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements", equalTo(1)))
            .andExpect(jsonPath("$.content[0].id", equalTo(orderId.intValue())));
    }
}
