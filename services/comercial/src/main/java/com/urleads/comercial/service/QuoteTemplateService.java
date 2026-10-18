package com.urleads.comercial.service;

import com.urleads.comercial.dto.InstantiateQuoteRequest;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.dto.QuoteDetailResponse;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteTemplateDetailResponse;
import com.urleads.comercial.dto.QuoteTemplateRequest;
import com.urleads.comercial.dto.QuoteTemplateResponse;
import com.urleads.comercial.entity.CatalogItem;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteTemplate;
import com.urleads.comercial.entity.QuoteTemplateItem;
import com.urleads.comercial.exception.DuplicateCodeException;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.repository.QuoteTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Transactional
public class QuoteTemplateService {

    private final QuoteTemplateRepository quoteTemplateRepository;
    private final QuoteRepository quoteRepository;
    private final CatalogItemRepository catalogItemRepository;

    public QuoteTemplateService(
        QuoteTemplateRepository quoteTemplateRepository,
        QuoteRepository quoteRepository,
        CatalogItemRepository catalogItemRepository
    ) {
        this.quoteTemplateRepository = quoteTemplateRepository;
        this.quoteRepository = quoteRepository;
        this.catalogItemRepository = catalogItemRepository;
    }

    public QuoteTemplateDetailResponse create(String ownerId, QuoteTemplateRequest request) {
        if (quoteTemplateRepository.existsByOwnerIdAndName(ownerId, request.name())) {
            throw new DuplicateCodeException("Ya existe una plantilla de cotización con el nombre: " + request.name());
        }

        BigDecimal taxRate = request.taxRate() != null ? request.taxRate() : new BigDecimal("0.1800");
        QuoteTemplate template = new QuoteTemplate(
            ownerId,
            request.name(),
            request.description(),
            request.currency(),
            taxRate,
            request.notes()
        );

        if (request.items() != null && !request.items().isEmpty()) {
            for (QuoteItemRequest itemReq : request.items()) {
                QuoteTemplateItem item = buildTemplateItem(template, ownerId, itemReq);
                template.addItem(item);
            }
        }

        QuoteTemplate saved = quoteTemplateRepository.save(template);
        return QuoteTemplateDetailResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteTemplateResponse> list(String ownerId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("El número de página no puede ser negativo.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor a 0.");
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<QuoteTemplate> pageResult = quoteTemplateRepository.findByOwnerId(ownerId, pageable);
        return PageResponse.from(pageResult, QuoteTemplateResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public QuoteTemplateDetailResponse getById(String ownerId, Long id) {
        QuoteTemplate template = findTemplateByOwner(ownerId, id);
        return QuoteTemplateDetailResponse.fromEntity(template);
    }

    public QuoteTemplateDetailResponse update(String ownerId, Long id, QuoteTemplateRequest request) {
        QuoteTemplate template = findTemplateByOwner(ownerId, id);

        if (!template.getName().equalsIgnoreCase(request.name())
            && quoteTemplateRepository.existsByOwnerIdAndName(ownerId, request.name())) {
            throw new DuplicateCodeException("Ya existe una plantilla de cotización con el nombre: " + request.name());
        }

        template.setName(request.name());
        template.setDescription(request.description());
        template.setCurrency(request.currency());
        if (request.taxRate() != null) {
            template.setTaxRate(request.taxRate());
        }
        template.setNotes(request.notes());

        if (request.items() != null) {
            template.getItems().clear();
            for (QuoteItemRequest itemReq : request.items()) {
                QuoteTemplateItem item = buildTemplateItem(template, ownerId, itemReq);
                template.addItem(item);
            }
        }

        QuoteTemplate saved = quoteTemplateRepository.save(template);
        return QuoteTemplateDetailResponse.fromEntity(saved);
    }

    public void delete(String ownerId, Long id) {
        QuoteTemplate template = findTemplateByOwner(ownerId, id);
        quoteTemplateRepository.delete(template);
    }

    /**
     * Instancia una cotización independiente a partir de una plantilla mediante copia profunda.
     */
    public QuoteDetailResponse instantiateQuote(String ownerId, Long templateId, InstantiateQuoteRequest request) {
        QuoteTemplate template = findTemplateByOwner(ownerId, templateId);

        BigDecimal effectiveTaxRate = request.taxRate() != null ? request.taxRate() : template.getTaxRate();
        String effectiveNotes = request.notes() != null ? request.notes() : template.getNotes();

        Quote quote = new Quote(
            request.leadId(),
            ownerId,
            template.getCurrency(),
            effectiveTaxRate,
            effectiveNotes
        );

        // Copia profunda de líneas de detalle
        if (template.getItems() != null && !template.getItems().isEmpty()) {
            for (QuoteTemplateItem templateItem : template.getItems()) {
                QuoteItem quoteItem = new QuoteItem(
                    quote,
                    templateItem.getCatalogItemId(),
                    templateItem.getCode(),
                    templateItem.getName(),
                    templateItem.getDescription(),
                    templateItem.getQuantity(),
                    templateItem.getUnitPrice()
                );
                quote.addItem(quoteItem);
            }
        } else {
            quote.recalculateTotals();
        }

        Quote savedQuote = quoteRepository.save(quote);
        return QuoteDetailResponse.fromEntity(savedQuote);
    }

    private QuoteTemplateItem buildTemplateItem(QuoteTemplate template, String ownerId, QuoteItemRequest itemReq) {
        if (itemReq.quantity() == null || itemReq.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }

        if (itemReq.catalogItemId() != null) {
            CatalogItem catalogItem = catalogItemRepository.findByIdAndOwnerId(itemReq.catalogItemId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem de catálogo no encontrado con ID: " + itemReq.catalogItemId()));

            if (!catalogItem.getCurrency().equalsIgnoreCase(template.getCurrency())) {
                throw new IllegalArgumentException("La moneda del ítem de catálogo (" + catalogItem.getCurrency() +
                    ") no coincide con la moneda de la plantilla (" + template.getCurrency() + ").");
            }

            return QuoteTemplateItem.fromCatalogSnapshot(
                template,
                catalogItem,
                itemReq.quantity(),
                itemReq.unitPrice()
            );
        } else {
            if (itemReq.code() == null || itemReq.code().isBlank()) {
                throw new IllegalArgumentException("El código es obligatorio para líneas manuales.");
            }
            if (itemReq.name() == null || itemReq.name().isBlank()) {
                throw new IllegalArgumentException("El nombre es obligatorio para líneas manuales.");
            }
            if (itemReq.unitPrice() == null || itemReq.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio unitario no puede ser negativo.");
            }

            return new QuoteTemplateItem(
                template,
                null,
                itemReq.code().trim(),
                itemReq.name().trim(),
                itemReq.description(),
                itemReq.quantity(),
                itemReq.unitPrice()
            );
        }
    }

    private QuoteTemplate findTemplateByOwner(String ownerId, Long id) {
        return quoteTemplateRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Plantilla de cotización no encontrada con ID: " + id));
    }
}
