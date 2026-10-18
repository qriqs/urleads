package com.urleads.comercial.service;

import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteDetailResponse;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteResponse;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import com.urleads.comercial.entity.CatalogItem;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.urleads.comercial.repository.QuoteItemRepository;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.event.QuotationAcceptedData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final CrmLeadService crmLeadService;
    private final OutboxEventPublisher outboxEventPublisher;

    public QuoteService(
        QuoteRepository quoteRepository,
        CatalogItemRepository catalogItemRepository,
        QuoteItemRepository quoteItemRepository,
        CrmLeadService crmLeadService,
        OutboxEventPublisher outboxEventPublisher
    ) {
        this.quoteRepository = quoteRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.quoteItemRepository = quoteItemRepository;
        this.crmLeadService = crmLeadService;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    public QuoteDetailResponse create(String ownerId, QuoteCreateRequest request) {
        BigDecimal taxRate = request.taxRate() != null ? request.taxRate() : new BigDecimal("0.1800");
        Quote quote = new Quote(request.leadId(), ownerId, request.currency(), taxRate, request.notes());

        if (request.items() != null && !request.items().isEmpty()) {
            for (QuoteItemRequest itemReq : request.items()) {
                QuoteItem item = buildQuoteItem(quote, ownerId, itemReq);
                quote.addItem(item);
            }
        } else {
            quote.recalculateTotals();
        }

        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteResponse> list(String ownerId, Long leadId, QuoteStatus status, String currency, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Quote> quotePage = quoteRepository.findByOwnerIdWithFilters(ownerId, leadId, status, currency, pageable);
        return PageResponse.from(quotePage, QuoteResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public QuoteDetailResponse getById(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);
        return QuoteDetailResponse.fromEntity(quote);
    }

    public QuoteDetailResponse update(String ownerId, Long id, QuoteUpdateRequest request) {
        Quote quote = findQuoteByOwner(ownerId, id);
        ensureDraftStatus(quote, "modificar");

        if (request.taxRate() != null) {
            quote.setTaxRate(request.taxRate());
        }
        if (request.notes() != null) {
            quote.setNotes(request.notes());
        }

        quote.recalculateTotals();
        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public void delete(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);
        ensureDraftStatus(quote, "eliminar");
        quoteRepository.delete(quote);
    }

    public QuoteDetailResponse addItem(String ownerId, Long quoteId, QuoteItemRequest itemReq) {
        Quote quote = findQuoteByOwner(ownerId, quoteId);
        ensureDraftStatus(quote, "agregar líneas a");

        QuoteItem item = buildQuoteItem(quote, ownerId, itemReq);
        QuoteItem savedItem = quoteItemRepository.save(item);
        quote.addItem(savedItem);

        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public QuoteDetailResponse removeItem(String ownerId, Long quoteId, Long itemId) {
        Quote quote = findQuoteByOwner(ownerId, quoteId);
        ensureDraftStatus(quote, "eliminar líneas de");

        QuoteItem toRemove = quote.getItems().stream()
            .filter(it -> it.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Línea de cotización no encontrada con ID: " + itemId));

        quote.removeItem(toRemove);
        quoteItemRepository.delete(toRemove);
        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public QuoteDetailResponse sendQuote(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);

        // Idempotencia: si ya fue enviada, retornar sin error
        if (quote.getStatus() == QuoteStatus.SENT) {
            return QuoteDetailResponse.fromEntity(quote);
        }

        if (quote.isFinal()) {
            throw new IllegalStateException("No se puede enviar una cotización en estado final (" + quote.getStatus() + ")");
        }

        quote.transitionTo(QuoteStatus.SENT);
        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public QuoteDetailResponse rejectQuote(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);

        // Idempotencia: si ya fue rechazada, retornar sin error
        if (quote.getStatus() == QuoteStatus.REJECTED) {
            return QuoteDetailResponse.fromEntity(quote);
        }

        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new IllegalStateException("No se puede rechazar una cotización que ya fue aceptada");
        }

        quote.transitionTo(QuoteStatus.REJECTED);
        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public QuoteDetailResponse acceptQuote(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);

        // Idempotencia: si ya fue aceptada, retornar respuesta sin duplicar eventos
        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            return QuoteDetailResponse.fromEntity(quote);
        }

        if (quote.getStatus() == QuoteStatus.REJECTED) {
            throw new IllegalStateException("No se puede aceptar una cotización en estado REJECTED");
        }

        // Validar existencia de lead en CRM via OpenFeign (S04)
        crmLeadService.getLeadById(quote.getLeadId());

        // Transición de estado a ACCEPTED
        quote.transitionTo(QuoteStatus.ACCEPTED);
        Quote savedQuote = quoteRepository.save(quote);

        // Registro atómico de evento en tabla outbox_events (sin PII)
        QuotationAcceptedData eventData = new QuotationAcceptedData(
            savedQuote.getId(),
            savedQuote.getLeadId(),
            savedQuote.getCurrency(),
            savedQuote.getTotal()
        );

        OutboxEvent outboxEvent = outboxEventPublisher.createAndSaveEvent(
            RabbitMqConfig.QUOTATION_ACCEPTED_ROUTING_KEY,
            1,
            savedQuote.getOwnerId(),
            eventData
        );

        // Despacho del evento hacia RabbitMQ
        outboxEventPublisher.publishEvent(outboxEvent);

        return QuoteDetailResponse.fromEntity(savedQuote);
    }

    private Quote findQuoteByOwner(String ownerId, Long id) {
        return quoteRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con ID: " + id));
    }

    private void ensureDraftStatus(Quote quote, String action) {
        if (quote.isFinal()) {
            throw new IllegalStateException("No se puede " + action + " una cotización en estado final (" + quote.getStatus() + ")");
        }
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden " + action + " cotizaciones en estado DRAFT");
        }
    }

    private QuoteItem buildQuoteItem(Quote quote, String ownerId, QuoteItemRequest itemReq) {
        String code = itemReq.code();
        String name = itemReq.name();
        String description = itemReq.description();
        BigDecimal unitPrice = itemReq.unitPrice();
        Long catalogItemId = itemReq.catalogItemId();

        if (catalogItemId != null) {
            CatalogItem cat = catalogItemRepository.findByIdAndOwnerId(catalogItemId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto o servicio del catálogo no encontrado con ID: " + catalogItemId));

            if (!cat.getCurrency().equalsIgnoreCase(quote.getCurrency())) {
                throw new IllegalArgumentException("La moneda del item del catálogo (" + cat.getCurrency() + ") no coincide con la moneda de la cotización (" + quote.getCurrency() + ")");
            }

            if (code == null || code.isBlank()) {
                code = cat.getCode();
            }
            if (name == null || name.isBlank()) {
                name = cat.getName();
            }
            if (description == null) {
                description = cat.getDescription();
            }
            if (unitPrice == null) {
                unitPrice = cat.getUnitPrice();
            }
        } else {
            if (code == null || code.isBlank()) {
                code = "CUSTOM";
            }
            if (name == null || name.isBlank()) {
                name = "Concepto";
            }
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
        }

        BigDecimal quantity = itemReq.quantity() != null ? itemReq.quantity() : BigDecimal.ONE;
        return new QuoteItem(quote, catalogItemId, code, name, description, quantity, unitPrice);
    }
}
