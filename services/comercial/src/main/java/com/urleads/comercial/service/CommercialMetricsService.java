package com.urleads.comercial.service;

import com.urleads.comercial.dto.CommercialCurrencyMetrics;
import com.urleads.comercial.dto.CommercialMetricsResponse;
import com.urleads.comercial.dto.OrderStatusMetric;
import com.urleads.comercial.dto.QuoteStatusMetric;
import com.urleads.comercial.entity.CommercialOrderStatus;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.CommercialOrderRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.repository.projection.OrderCurrencyStatusAggregation;
import com.urleads.comercial.repository.projection.QuoteCurrencyStatusAggregation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@Transactional(readOnly = true)
public class CommercialMetricsService {

    private final QuoteRepository quoteRepository;
    private final CommercialOrderRepository commercialOrderRepository;

    public CommercialMetricsService(
        QuoteRepository quoteRepository,
        CommercialOrderRepository commercialOrderRepository
    ) {
        this.quoteRepository = quoteRepository;
        this.commercialOrderRepository = commercialOrderRepository;
    }

    /**
     * Calcula métricas y totales de cotizaciones y órdenes estrictamente desagregados por moneda.
     * Prohíbe cualquier consolidación o suma de importes entre monedas distintas.
     */
    public CommercialMetricsResponse getMetrics(String ownerId, String currencyFilter) {
        List<QuoteCurrencyStatusAggregation> quoteAggregations = quoteRepository.aggregateMetricsByOwner(ownerId);
        List<OrderCurrencyStatusAggregation> orderAggregations = commercialOrderRepository.aggregateMetricsByOwner(ownerId);

        Set<String> currencies = new TreeSet<>();
        for (QuoteCurrencyStatusAggregation qa : quoteAggregations) {
            if (qa.getCurrency() != null && !qa.getCurrency().isBlank()) {
                currencies.add(qa.getCurrency().toUpperCase());
            }
        }
        for (OrderCurrencyStatusAggregation oa : orderAggregations) {
            if (oa.getCurrency() != null && !oa.getCurrency().isBlank()) {
                currencies.add(oa.getCurrency().toUpperCase());
            }
        }

        if (currencyFilter != null && !currencyFilter.isBlank()) {
            String targetCurrency = currencyFilter.trim().toUpperCase();
            currencies.clear();
            currencies.add(targetCurrency);
        }

        List<CommercialCurrencyMetrics> metricsList = new ArrayList<>();

        for (String curr : currencies) {
            // 1. Agrupar cotizaciones por estado para esta moneda
            Map<String, QuoteStatusMetric> quotesByStatus = new LinkedHashMap<>();
            long totalQuotes = 0;
            BigDecimal totalQuotesSubtotal = BigDecimal.ZERO;
            BigDecimal totalQuotesTax = BigDecimal.ZERO;
            BigDecimal totalQuotesAmount = BigDecimal.ZERO;
            long acceptedQuotes = 0;
            BigDecimal acceptedQuotesAmount = BigDecimal.ZERO;

            for (QuoteCurrencyStatusAggregation qa : quoteAggregations) {
                if (curr.equalsIgnoreCase(qa.getCurrency())) {
                    long count = qa.getCount() != null ? qa.getCount() : 0L;
                    BigDecimal subtotal = qa.getSubtotal() != null ? qa.getSubtotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                    BigDecimal tax = qa.getTax() != null ? qa.getTax().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                    BigDecimal total = qa.getTotal() != null ? qa.getTotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

                    totalQuotes += count;
                    totalQuotesSubtotal = totalQuotesSubtotal.add(subtotal);
                    totalQuotesTax = totalQuotesTax.add(tax);
                    totalQuotesAmount = totalQuotesAmount.add(total);

                    if (qa.getStatus() == QuoteStatus.ACCEPTED) {
                        acceptedQuotes += count;
                        acceptedQuotesAmount = acceptedQuotesAmount.add(total);
                    }

                    quotesByStatus.put(qa.getStatus().name(), new QuoteStatusMetric(
                        qa.getStatus(),
                        count,
                        subtotal,
                        tax,
                        total
                    ));
                }
            }

            // Tasa de conversión: % de cotizaciones aceptadas
            BigDecimal conversionRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (totalQuotes > 0) {
                conversionRate = BigDecimal.valueOf(acceptedQuotes)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalQuotes), 2, RoundingMode.HALF_UP);
            }

            // 2. Agrupar órdenes por estado para esta moneda
            Map<String, OrderStatusMetric> ordersByStatus = new LinkedHashMap<>();
            long totalOrders = 0;
            BigDecimal totalOrdersSubtotal = BigDecimal.ZERO;
            BigDecimal totalOrdersTax = BigDecimal.ZERO;
            BigDecimal totalOrdersAmount = BigDecimal.ZERO;

            for (OrderCurrencyStatusAggregation oa : orderAggregations) {
                if (curr.equalsIgnoreCase(oa.getCurrency())) {
                    long count = oa.getCount() != null ? oa.getCount() : 0L;
                    BigDecimal subtotal = oa.getSubtotal() != null ? oa.getSubtotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                    BigDecimal tax = oa.getTax() != null ? oa.getTax().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                    BigDecimal total = oa.getTotal() != null ? oa.getTotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

                    totalOrders += count;
                    totalOrdersSubtotal = totalOrdersSubtotal.add(subtotal);
                    totalOrdersTax = totalOrdersTax.add(tax);
                    totalOrdersAmount = totalOrdersAmount.add(total);

                    ordersByStatus.put(oa.getStatus().name(), new OrderStatusMetric(
                        oa.getStatus(),
                        count,
                        subtotal,
                        tax,
                        total
                    ));
                }
            }

            metricsList.add(new CommercialCurrencyMetrics(
                curr,
                totalQuotes,
                totalQuotesSubtotal.setScale(2, RoundingMode.HALF_UP),
                totalQuotesTax.setScale(2, RoundingMode.HALF_UP),
                totalQuotesAmount.setScale(2, RoundingMode.HALF_UP),
                acceptedQuotes,
                acceptedQuotesAmount.setScale(2, RoundingMode.HALF_UP),
                conversionRate,
                quotesByStatus,
                totalOrders,
                totalOrdersSubtotal.setScale(2, RoundingMode.HALF_UP),
                totalOrdersTax.setScale(2, RoundingMode.HALF_UP),
                totalOrdersAmount.setScale(2, RoundingMode.HALF_UP),
                ordersByStatus
            ));
        }

        return new CommercialMetricsResponse(ownerId, metricsList);
    }
}
