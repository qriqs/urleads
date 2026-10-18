package com.urleads.comercial.service;

import com.urleads.comercial.dto.CommercialOrderDetailResponse;
import com.urleads.comercial.dto.CommercialOrderResponse;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.entity.CommercialOrder;
import com.urleads.comercial.entity.CommercialOrderItem;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CommercialOrderRepository;
import com.urleads.comercial.repository.QuoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class CommercialOrderService {

    private final CommercialOrderRepository commercialOrderRepository;
    private final QuoteRepository quoteRepository;

    public CommercialOrderService(
        CommercialOrderRepository commercialOrderRepository,
        QuoteRepository quoteRepository
    ) {
        this.commercialOrderRepository = commercialOrderRepository;
        this.quoteRepository = quoteRepository;
    }

    /**
     * Genera de forma estrictamente idempotente una orden comercial a partir de una cotización aceptada.
     * Si la orden ya existe para dicha cotización, retorna la orden existente sin duplicar registros.
     */
    public CommercialOrderDetailResponse generateOrderFromQuote(String ownerId, Long quoteId) {
        Quote quote = quoteRepository.findByIdAndOwnerId(quoteId, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con ID: " + quoteId));

        // Idempotencia: si ya existe una orden generada para esta cotización, retornarla
        Optional<CommercialOrder> existingOrder = commercialOrderRepository.findByOwnerIdAndQuoteId(ownerId, quoteId);
        if (existingOrder.isPresent()) {
            return CommercialOrderDetailResponse.fromEntity(existingOrder.get());
        }

        // Validación de estado de la cotización: debe estar en ACCEPTED
        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new IllegalStateException("Solo se pueden generar órdenes comerciales a partir de cotizaciones en estado ACCEPTED. Estado actual: " + quote.getStatus());
        }

        // Validación de versión: no se puede generar orden sobre una versión anterior superada
        if (Boolean.FALSE.equals(quote.isLatest())) {
            throw new IllegalStateException("No se puede generar una orden comercial a partir de una versión anterior superada de la cotización (v" + quote.getRevisionNumber() + ").");
        }

        String orderNumber = String.format("ORD-%06d", quote.getId());

        CommercialOrder order = new CommercialOrder(
            orderNumber,
            quote.getId(),
            quote.getLeadId(),
            ownerId,
            quote.getCurrency(),
            quote.getSubtotal(),
            quote.getTaxRate(),
            quote.getTax(),
            quote.getTotal(),
            quote.getNotes()
        );

        if (quote.getItems() != null && !quote.getItems().isEmpty()) {
            for (QuoteItem quoteItem : quote.getItems()) {
                CommercialOrderItem orderItem = new CommercialOrderItem(
                    order,
                    quoteItem.getCatalogItemId(),
                    quoteItem.getCode(),
                    quoteItem.getName(),
                    quoteItem.getDescription(),
                    quoteItem.getQuantity(),
                    quoteItem.getUnitPrice()
                );
                order.addItem(orderItem);
            }
        }

        try {
            CommercialOrder saved = commercialOrderRepository.save(order);
            return CommercialOrderDetailResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            // Manejo de condición de carrera concurrente protegida por la restricción UNIQUE(quote_id)
            return commercialOrderRepository.findByOwnerIdAndQuoteId(ownerId, quoteId)
                .map(CommercialOrderDetailResponse::fromEntity)
                .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public CommercialOrderDetailResponse getOrderByQuoteId(String ownerId, Long quoteId) {
        // Asegurar que la cotización existe y pertenece al usuario
        quoteRepository.findByIdAndOwnerId(quoteId, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con ID: " + quoteId));

        CommercialOrder order = commercialOrderRepository.findByOwnerIdAndQuoteId(ownerId, quoteId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró orden comercial para la cotización con ID: " + quoteId));

        return CommercialOrderDetailResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public CommercialOrderDetailResponse getById(String ownerId, Long id) {
        CommercialOrder order = commercialOrderRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Orden comercial no encontrada con ID: " + id));

        return CommercialOrderDetailResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommercialOrderResponse> list(String ownerId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("El número de página no puede ser negativo.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor a 0.");
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<CommercialOrder> orderPage = commercialOrderRepository.findByOwnerId(ownerId, pageable);
        return PageResponse.from(orderPage, CommercialOrderResponse::fromEntity);
    }
}
