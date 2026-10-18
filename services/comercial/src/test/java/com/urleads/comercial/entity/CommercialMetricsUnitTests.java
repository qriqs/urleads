package com.urleads.comercial.entity;

import com.urleads.comercial.dto.CommercialMetricsResponse;
import com.urleads.comercial.dto.CommercialCurrencyMetrics;
import com.urleads.comercial.repository.CommercialOrderRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.repository.projection.OrderCurrencyStatusAggregation;
import com.urleads.comercial.repository.projection.QuoteCurrencyStatusAggregation;
import com.urleads.comercial.service.CommercialMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialMetricsUnitTests {

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private CommercialOrderRepository commercialOrderRepository;

    @InjectMocks
    private CommercialMetricsService commercialMetricsService;

    private QuoteCurrencyStatusAggregation mockQuoteAgg(
        String currency, QuoteStatus status, long count, double subtotal, double tax, double total
    ) {
        return new QuoteCurrencyStatusAggregation() {
            @Override public String getCurrency() { return currency; }
            @Override public QuoteStatus getStatus() { return status; }
            @Override public Long getCount() { return count; }
            @Override public BigDecimal getSubtotal() { return BigDecimal.valueOf(subtotal); }
            @Override public BigDecimal getTax() { return BigDecimal.valueOf(tax); }
            @Override public BigDecimal getTotal() { return BigDecimal.valueOf(total); }
        };
    }

    private OrderCurrencyStatusAggregation mockOrderAgg(
        String currency, CommercialOrderStatus status, long count, double subtotal, double tax, double total
    ) {
        return new OrderCurrencyStatusAggregation() {
            @Override public String getCurrency() { return currency; }
            @Override public CommercialOrderStatus getStatus() { return status; }
            @Override public Long getCount() { return count; }
            @Override public BigDecimal getSubtotal() { return BigDecimal.valueOf(subtotal); }
            @Override public BigDecimal getTax() { return BigDecimal.valueOf(tax); }
            @Override public BigDecimal getTotal() { return BigDecimal.valueOf(total); }
        };
    }

    @Test
    @DisplayName("getMetrics strictly separates PEN and USD without currency cross-consolidation")
    void getMetrics_strictlySeparatesCurrencies() {
        when(quoteRepository.aggregateMetricsByOwner("42")).thenReturn(List.of(
            mockQuoteAgg("USD", QuoteStatus.DRAFT, 2, 200.0, 36.0, 236.0),
            mockQuoteAgg("USD", QuoteStatus.ACCEPTED, 2, 800.0, 144.0, 944.0),
            mockQuoteAgg("PEN", QuoteStatus.SENT, 5, 1000.0, 180.0, 1180.0)
        ));

        when(commercialOrderRepository.aggregateMetricsByOwner("42")).thenReturn(List.of(
            mockOrderAgg("USD", CommercialOrderStatus.CONFIRMED, 2, 800.0, 144.0, 944.0)
        ));

        CommercialMetricsResponse response = commercialMetricsService.getMetrics("42", null);

        assertThat(response.ownerId()).isEqualTo("42");
        assertThat(response.metricsByCurrency()).hasSize(2);

        // PEN metrics
        CommercialCurrencyMetrics pen = response.metricsByCurrency().stream()
            .filter(m -> "PEN".equals(m.currency())).findFirst().orElseThrow();
        assertThat(pen.totalQuotes()).isEqualTo(5);
        assertThat(pen.totalQuotesAmount()).isEqualByComparingTo(new BigDecimal("1180.00"));
        assertThat(pen.acceptedQuotes()).isEqualTo(0);
        assertThat(pen.quoteConversionRatePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pen.totalOrders()).isEqualTo(0);
        assertThat(pen.totalOrdersAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // USD metrics
        CommercialCurrencyMetrics usd = response.metricsByCurrency().stream()
            .filter(m -> "USD".equals(m.currency())).findFirst().orElseThrow();
        assertThat(usd.totalQuotes()).isEqualTo(4);
        assertThat(usd.totalQuotesAmount()).isEqualByComparingTo(new BigDecimal("1180.00"));
        assertThat(usd.acceptedQuotes()).isEqualTo(2);
        assertThat(usd.quoteConversionRatePercentage()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(usd.totalOrders()).isEqualTo(2);
        assertThat(usd.totalOrdersAmount()).isEqualByComparingTo(new BigDecimal("944.00"));
    }

    @Test
    @DisplayName("getMetrics filters strictly by requested currency when provided")
    void getMetrics_filtersByCurrency() {
        when(quoteRepository.aggregateMetricsByOwner("42")).thenReturn(List.of(
            mockQuoteAgg("USD", QuoteStatus.ACCEPTED, 1, 100.0, 18.0, 118.0),
            mockQuoteAgg("PEN", QuoteStatus.ACCEPTED, 1, 500.0, 90.0, 590.0)
        ));
        when(commercialOrderRepository.aggregateMetricsByOwner("42")).thenReturn(List.of());

        CommercialMetricsResponse response = commercialMetricsService.getMetrics("42", "usd");

        assertThat(response.metricsByCurrency()).hasSize(1);
        CommercialCurrencyMetrics usd = response.metricsByCurrency().get(0);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.totalQuotes()).isEqualTo(1);
        assertThat(usd.totalQuotesAmount()).isEqualByComparingTo(new BigDecimal("118.00"));
    }

    @Test
    @DisplayName("getMetrics returns empty list when owner has no commercial data")
    void getMetrics_emptyWhenNoData() {
        when(quoteRepository.aggregateMetricsByOwner("99")).thenReturn(List.of());
        when(commercialOrderRepository.aggregateMetricsByOwner("99")).thenReturn(List.of());

        CommercialMetricsResponse response = commercialMetricsService.getMetrics("99", null);

        assertThat(response.ownerId()).isEqualTo("99");
        assertThat(response.metricsByCurrency()).isEmpty();
    }
}
