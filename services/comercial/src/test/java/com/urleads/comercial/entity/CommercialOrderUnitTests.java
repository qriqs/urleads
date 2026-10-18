package com.urleads.comercial.entity;

import com.urleads.comercial.dto.CommercialOrderDetailResponse;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CommercialOrderRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.service.CommercialOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialOrderUnitTests {

    @Mock
    private CommercialOrderRepository commercialOrderRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @InjectMocks
    private CommercialOrderService commercialOrderService;

    private Quote acceptedQuote;

    @BeforeEach
    void setUp() {
        acceptedQuote = new Quote(100L, "42", "USD", new BigDecimal("0.1800"), "Cotización formal");
        acceptedQuote.setId(55L);
        acceptedQuote.setStatus(QuoteStatus.ACCEPTED);
        acceptedQuote.setLatest(true);

        QuoteItem item = new QuoteItem(
            acceptedQuote,
            10L,
            "ITM-01",
            "Servicio Cloud",
            "Licencia anual",
            new BigDecimal("2.00"),
            new BigDecimal("500.00")
        );
        acceptedQuote.addItem(item);
    }

    @Test
    @DisplayName("generateOrderFromQuote successfully generates order from ACCEPTED quote")
    void generateOrder_success() {
        when(quoteRepository.findByIdAndOwnerId(55L, "42")).thenReturn(Optional.of(acceptedQuote));
        when(commercialOrderRepository.findByOwnerIdAndQuoteId("42", 55L)).thenReturn(Optional.empty());

        when(commercialOrderRepository.save(any(CommercialOrder.class))).thenAnswer(invocation -> {
            CommercialOrder order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        CommercialOrderDetailResponse res = commercialOrderService.generateOrderFromQuote("42", 55L);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.orderNumber()).isEqualTo("ORD-000055");
        assertThat(res.quoteId()).isEqualTo(55L);
        assertThat(res.leadId()).isEqualTo(100L);
        assertThat(res.ownerId()).isEqualTo("42");
        assertThat(res.currency()).isEqualTo("USD");
        assertThat(res.status()).isEqualTo(CommercialOrderStatus.CONFIRMED);
        assertThat(res.subtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(res.tax()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("1180.00"));
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).code()).isEqualTo("ITM-01");

        verify(commercialOrderRepository, times(1)).save(any(CommercialOrder.class));
    }

    @Test
    @DisplayName("generateOrderFromQuote is idempotent and returns existing order without re-saving")
    void generateOrder_idempotent() {
        CommercialOrder existingOrder = new CommercialOrder(
            "ORD-000055",
            55L,
            100L,
            "42",
            "USD",
            new BigDecimal("1000.00"),
            new BigDecimal("0.1800"),
            new BigDecimal("180.00"),
            new BigDecimal("1180.00"),
            "Notas"
        );
        existingOrder.setId(1L);

        when(quoteRepository.findByIdAndOwnerId(55L, "42")).thenReturn(Optional.of(acceptedQuote));
        when(commercialOrderRepository.findByOwnerIdAndQuoteId("42", 55L)).thenReturn(Optional.of(existingOrder));

        CommercialOrderDetailResponse res = commercialOrderService.generateOrderFromQuote("42", 55L);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.orderNumber()).isEqualTo("ORD-000055");

        verify(commercialOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateOrderFromQuote rejects quotes not in ACCEPTED state")
    void generateOrder_rejectsNonAcceptedQuotes() {
        acceptedQuote.setStatus(QuoteStatus.DRAFT);
        when(quoteRepository.findByIdAndOwnerId(55L, "42")).thenReturn(Optional.of(acceptedQuote));
        when(commercialOrderRepository.findByOwnerIdAndQuoteId("42", 55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commercialOrderService.generateOrderFromQuote("42", 55L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Solo se pueden generar órdenes comerciales a partir de cotizaciones en estado ACCEPTED");

        acceptedQuote.setStatus(QuoteStatus.SENT);
        assertThatThrownBy(() -> commercialOrderService.generateOrderFromQuote("42", 55L))
            .isInstanceOf(IllegalStateException.class);

        acceptedQuote.setStatus(QuoteStatus.REJECTED);
        assertThatThrownBy(() -> commercialOrderService.generateOrderFromQuote("42", 55L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("generateOrderFromQuote rejects superseded quote versions")
    void generateOrder_rejectsSupersededQuote() {
        acceptedQuote.setLatest(false);
        when(quoteRepository.findByIdAndOwnerId(55L, "42")).thenReturn(Optional.of(acceptedQuote));
        when(commercialOrderRepository.findByOwnerIdAndQuoteId("42", 55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commercialOrderService.generateOrderFromQuote("42", 55L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No se puede generar una orden comercial a partir de una versión anterior superada");
    }

    @Test
    @DisplayName("generateOrderFromQuote throws ResourceNotFoundException when quote does not exist or owner differs")
    void generateOrder_quoteNotFound() {
        when(quoteRepository.findByIdAndOwnerId(999L, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commercialOrderService.generateOrderFromQuote("42", 999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Cotización no encontrada");
    }
}
