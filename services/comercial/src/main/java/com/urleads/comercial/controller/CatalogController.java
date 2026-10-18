package com.urleads.comercial.controller;

import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.dto.CatalogItemResponse;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<CatalogItemResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CatalogItemRequest request
    ) {
        String ownerId = jwt.getSubject();
        CatalogItemResponse created = catalogService.create(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<CatalogItemResponse>> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String ownerId = jwt.getSubject();
        PageResponse<CatalogItemResponse> result = catalogService.list(ownerId, currency, active, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogItemResponse> getById(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        CatalogItemResponse item = catalogService.getById(ownerId, id);
        return ResponseEntity.ok(item);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatalogItemResponse> update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody CatalogItemRequest request
    ) {
        String ownerId = jwt.getSubject();
        CatalogItemResponse updated = catalogService.update(ownerId, id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        catalogService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }
}
