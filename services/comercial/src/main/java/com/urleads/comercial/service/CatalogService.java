package com.urleads.comercial.service;

import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.dto.CatalogItemResponse;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.entity.CatalogItem;
import com.urleads.comercial.exception.DuplicateCodeException;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.repository.CatalogItemRepository;
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
public class CatalogService {

    private final CatalogItemRepository catalogItemRepository;

    public CatalogService(CatalogItemRepository catalogItemRepository) {
        this.catalogItemRepository = catalogItemRepository;
    }

    public CatalogItemResponse create(String ownerId, CatalogItemRequest request) {
        if (catalogItemRepository.existsByOwnerIdAndCode(ownerId, request.code())) {
            throw new DuplicateCodeException("Ya existe un ítem en el catálogo con el código especificado: " + request.code());
        }

        BigDecimal unitPrice = request.unitPrice().setScale(2, RoundingMode.HALF_UP);
        CatalogItem item = new CatalogItem(
            ownerId,
            request.code(),
            request.name(),
            request.description(),
            request.currency(),
            unitPrice,
            request.active()
        );

        CatalogItem saved = catalogItemRepository.save(item);
        return CatalogItemResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogItemResponse> list(String ownerId, String currency, Boolean active, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<CatalogItem> itemsPage = catalogItemRepository.findByOwnerIdWithFilters(
            ownerId,
            currency,
            active,
            pageable
        );

        return PageResponse.from(itemsPage, CatalogItemResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse getById(String ownerId, Long id) {
        CatalogItem item = catalogItemRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto o servicio no encontrado con ID: " + id));
        return CatalogItemResponse.fromEntity(item);
    }

    public CatalogItemResponse update(String ownerId, Long id, CatalogItemRequest request) {
        CatalogItem item = catalogItemRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto o servicio no encontrado con ID: " + id));

        if (catalogItemRepository.existsByOwnerIdAndCodeAndIdNot(ownerId, request.code(), id)) {
            throw new DuplicateCodeException("Ya existe otro ítem con el código especificado: " + request.code());
        }

        item.setCode(request.code());
        item.setName(request.name());
        item.setDescription(request.description());
        item.setCurrency(request.currency());
        item.setUnitPrice(request.unitPrice());
        if (request.active() != null) {
            item.setActive(request.active());
        }

        CatalogItem updated = catalogItemRepository.save(item);
        return CatalogItemResponse.fromEntity(updated);
    }

    public void delete(String ownerId, Long id) {
        CatalogItem item = catalogItemRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto o servicio no encontrado con ID: " + id));
        catalogItemRepository.delete(item);
    }
}
