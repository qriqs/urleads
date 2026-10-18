package com.urleads.comercial.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommercialMetricsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CommercialOrderRepository commercialOrderRepository;

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        commercialOrderRepository.deleteAll();
        quoteRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    private Quote createQuote(String ownerId, String currency, QuoteStatus status, BigDecimal subtotal, BigDecimal taxRate) {
        Quote quote = new Quote(100L, ownerId, currency, taxRate, "Cotización de prueba");
        quote.setStatus(status);
        quote.setSubtotal(subtotal);
        BigDecimal tax = subtotal.multiply(taxRate != null ? taxRate : BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        quote.setTax(tax);
        quote.setTotal(subtotal.add(tax));
        quote.setLatest(true);
        return quoteRepository.save(quote);
    }

    @Test
    @DisplayName("GET /api/v1/metrics returns metrics grouped by currency without cross-currency consolidation")
    void getMetrics_returnsSeparatedCurrencies() throws Exception {
        // User 42 quotes in USD
        Quote qUsd1 = createQuote("42", "USD", QuoteStatus.DRAFT, new BigDecimal("200.00"), new BigDecimal("0.1800"));
        Quote qUsd2 = createQuote("42", "USD", QuoteStatus.ACCEPTED, new BigDecimal("800.00"), new BigDecimal("0.1800"));

        // User 42 commercial order in USD
        CommercialOrder orderUsd = new CommercialOrder(
            "ORD-000001",
            qUsd2.getId(),
            100L,
            "42",
            "USD",
            new BigDecimal("800.00"),
            new BigDecimal("0.1800"),
            new BigDecimal("144.00"),
            new BigDecimal("944.00"),
            "Orden de prueba"
        );
        commercialOrderRepository.save(orderUsd);

        // User 42 quotes in PEN
        createQuote("42", "PEN", QuoteStatus.SENT, new BigDecimal("1000.00"), new BigDecimal("0.1800"));
        createQuote("42", "PEN", QuoteStatus.REJECTED, new BigDecimal("500.00"), new BigDecimal("0.1800"));

        // Foreign User 99 quote in USD (should not pollute User 42's metrics)
        createQuote("99", "USD", QuoteStatus.ACCEPTED, new BigDecimal("9999.00"), BigDecimal.ZERO);

        mockMvc.perform(get("/api/v1/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerId", equalTo("42")))
            .andExpect(jsonPath("$.metricsByCurrency", hasSize(2)))
            // PEN metrics
            .andExpect(jsonPath("$.metricsByCurrency[0].currency", equalTo("PEN")))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalQuotes", equalTo(2)))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalQuotesAmount", equalTo(1770.0)))
            .andExpect(jsonPath("$.metricsByCurrency[0].acceptedQuotes", equalTo(0)))
            .andExpect(jsonPath("$.metricsByCurrency[0].quoteConversionRatePercentage", equalTo(0.0)))
            .andExpect(jsonPath("$.metricsByCurrency[0].quotesByStatus.SENT.count", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[0].quotesByStatus.REJECTED.count", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalOrders", equalTo(0)))
            // USD metrics
            .andExpect(jsonPath("$.metricsByCurrency[1].currency", equalTo("USD")))
            .andExpect(jsonPath("$.metricsByCurrency[1].totalQuotes", equalTo(2)))
            .andExpect(jsonPath("$.metricsByCurrency[1].totalQuotesAmount", equalTo(1180.0)))
            .andExpect(jsonPath("$.metricsByCurrency[1].acceptedQuotes", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[1].quoteConversionRatePercentage", equalTo(50.0)))
            .andExpect(jsonPath("$.metricsByCurrency[1].quotesByStatus.DRAFT.count", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[1].quotesByStatus.ACCEPTED.count", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[1].totalOrders", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[1].totalOrdersAmount", equalTo(944.0)));
    }

    @Test
    @DisplayName("GET /api/v1/metrics?currency=USD filters metrics only to requested currency")
    void getMetrics_withCurrencyFilter() throws Exception {
        createQuote("42", "USD", QuoteStatus.ACCEPTED, new BigDecimal("500.00"), new BigDecimal("0.1800"));
        createQuote("42", "PEN", QuoteStatus.ACCEPTED, new BigDecimal("2000.00"), new BigDecimal("0.1800"));

        mockMvc.perform(get("/api/v1/metrics?currency=USD")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metricsByCurrency", hasSize(1)))
            .andExpect(jsonPath("$.metricsByCurrency[0].currency", equalTo("USD")))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalQuotes", equalTo(1)))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalQuotesAmount", equalTo(590.0)));
    }

    @Test
    @DisplayName("GET /api/v1/metrics enforces strict owner isolation between users")
    void getMetrics_enforcesOwnerIsolation() throws Exception {
        createQuote("42", "USD", QuoteStatus.ACCEPTED, new BigDecimal("1000.00"), BigDecimal.ZERO);
        createQuote("99", "USD", QuoteStatus.ACCEPTED, new BigDecimal("777.00"), BigDecimal.ZERO);

        // User 99 only sees User 99's USD data
        mockMvc.perform(get("/api/v1/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerId", equalTo("99")))
            .andExpect(jsonPath("$.metricsByCurrency", hasSize(1)))
            .andExpect(jsonPath("$.metricsByCurrency[0].totalQuotesAmount", equalTo(777.0)));
    }

    @Test
    @DisplayName("GET /api/v1/metrics without authentication returns HTTP 401 Unauthorized")
    void getMetrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
            .andExpect(status().isUnauthorized());
    }
}
