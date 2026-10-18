package com.urleads.comercial.service;

import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteDetailResponse;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteOutcomeRequest;
import com.urleads.comercial.dto.QuoteResponse;
import com.urleads.comercial.dto.QuoteRevisionRequest;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import java.util.List;
import com.urleads.comercial.entity.CatalogItem;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteOutcomeReason;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.QuoteItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.repository.QuoteSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.event.QuotationAcceptedData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
        return list(ownerId, leadId, status, currency, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteResponse> list(
        String ownerId,
        Long leadId,
        QuoteStatus status,
        String currency,
        QuoteOutcomeReason outcomeReason,
        String dateFromStr,
        String dateToStr,
        String searchTerm,
        int page,
        int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("El número de página no puede ser negativo.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor a 0.");
        }

        Instant fromInstant = parseDateFrom(dateFromStr);
        Instant toInstant = parseDateTo(dateToStr);

        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new IllegalArgumentException("El rango de fechas es inválido: 'dateFrom' no puede ser posterior a 'dateTo'.");
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Quote> spec = QuoteSpecification.withFilters(
            ownerId,
            leadId,
            status,
            currency,
            outcomeReason,
            fromInstant,
            toInstant,
            searchTerm
        );

        Page<Quote> quotePage = quoteRepository.findAll(spec, pageable);
        return PageResponse.from(quotePage, QuoteResponse::fromEntity);
    }

    private Instant parseDateFrom(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            if (trimmed.length() == 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(trimmed);
        } catch (Exception e1) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (Exception e2) {
                try {
                    return LocalDateTime.parse(trimmed).atZone(ZoneOffset.UTC).toInstant();
                } catch (Exception e3) {
                    throw new IllegalArgumentException("Formato inválido para parámetro 'dateFrom': '" + input +
                        "'. Utilice formato ISO-8601 (por ejemplo, '2026-10-18' o '2026-10-18T00:00:00Z').");
                }
            }
        }
    }

    private Instant parseDateTo(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            if (trimmed.length() == 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                return LocalDate.parse(trimmed).atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(trimmed);
        } catch (Exception e1) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (Exception e2) {
                try {
                    return LocalDateTime.parse(trimmed).atZone(ZoneOffset.UTC).toInstant();
                } catch (Exception e3) {
                    throw new IllegalArgumentException("Formato inválido para parámetro 'dateTo': '" + input +
                        "'. Utilice formato ISO-8601 (por ejemplo, '2026-10-18' o '2026-10-18T23:59:59Z').");
                }
            }
        }
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

        if (Boolean.FALSE.equals(quote.isLatest())) {
            throw new IllegalStateException("No se puede enviar una versión superada de la cotización (v" + quote.getRevisionNumber() + ")");
        }

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
        return rejectQuote(ownerId, id, null);
    }

    public QuoteDetailResponse rejectQuote(String ownerId, Long id, QuoteOutcomeRequest outcomeRequest) {
        Quote quote = findQuoteByOwner(ownerId, id);

        if (Boolean.FALSE.equals(quote.isLatest())) {
            throw new IllegalStateException("No se puede rechazar una versión superada de la cotización (v" + quote.getRevisionNumber() + ")");
        }

        // Idempotencia: si ya fue rechazada, actualizar motivo si viene en la solicitud y retornar
        if (quote.getStatus() == QuoteStatus.REJECTED) {
            if (outcomeRequest != null) {
                applyOutcome(quote, outcomeRequest);
                Quote saved = quoteRepository.save(quote);
                return QuoteDetailResponse.fromEntity(saved);
            }
            return QuoteDetailResponse.fromEntity(quote);
        }

        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new IllegalStateException("No se puede rechazar una cotización que ya fue aceptada");
        }

        quote.transitionTo(QuoteStatus.REJECTED);
        if (outcomeRequest != null) {
            applyOutcome(quote, outcomeRequest);
        } else {
            if (quote.getOutcomeDate() == null) {
                quote.setOutcomeDate(Instant.now());
            }
        }
        Quote saved = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(saved);
    }

    public QuoteDetailResponse recordOutcome(String ownerId, Long id, QuoteOutcomeRequest request) {
        return rejectQuote(ownerId, id, request);
    }

    private void applyOutcome(Quote quote, QuoteOutcomeRequest request) {
        quote.setOutcomeReason(request.reason());
        quote.setOutcomeNotes(request.notes());
        quote.setOutcomeDate(request.date() != null ? request.date() : Instant.now());
    }

    public QuoteDetailResponse acceptQuote(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);

        if (Boolean.FALSE.equals(quote.isLatest())) {
            throw new IllegalStateException("No se puede aceptar una versión superada de la cotización (v" + quote.getRevisionNumber() + ")");
        }

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

    /**
     * Crea una nueva revisión inmutable (v(N+1)) a partir de una cotización existente.
     * Bloquea la versión previa dejándola como no-última (isLatest = false).
     */
    public QuoteDetailResponse createRevision(String ownerId, Long id, QuoteRevisionRequest request) {
        Quote previousQuote = findQuoteByOwner(ownerId, id);

        if (Boolean.FALSE.equals(previousQuote.isLatest())) {
            throw new IllegalStateException("No se puede generar una revisión a partir de una versión anterior que ya ha sido superada. Utilice la versión activa (v" + previousQuote.getRevisionNumber() + ").");
        }

        Quote nextRevision = previousQuote.createNextRevision(
            request != null ? request.notes() : null,
            request != null ? request.taxRate() : null
        );

        if (previousQuote.getRootQuoteId() == null) {
            previousQuote.setRootQuoteId(previousQuote.getId());
            nextRevision.setRootQuoteId(previousQuote.getId());
        }

        quoteRepository.save(previousQuote);
        Quote saved = quoteRepository.save(nextRevision);
        return QuoteDetailResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> getRevisions(String ownerId, Long id) {
        Quote quote = findQuoteByOwner(ownerId, id);
        Long rootId = quote.getRootQuoteId() != null ? quote.getRootQuoteId() : quote.getId();
        List<Quote> revisions = quoteRepository.findRevisionsByRootId(ownerId, rootId);
        return revisions.stream().map(QuoteResponse::fromEntity).toList();
    }

    private Quote findQuoteByOwner(String ownerId, Long id) {
        return quoteRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con ID: " + id));
    }

    private void ensureDraftStatus(Quote quote, String action) {
        if (Boolean.FALSE.equals(quote.isLatest())) {
            throw new IllegalStateException("No se puede " + action + " una versión anterior superada de la cotización (v" + quote.getRevisionNumber() + ")");
        }
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
