package com.urleads.comercial.controller;

import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteDetailResponse;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.dto.QuoteResponse;
import com.urleads.comercial.dto.QuoteUpdateRequest;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.service.QuoteService;
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
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteDetailResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody QuoteCreateRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.create(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<QuoteResponse>> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long leadId,
        @RequestParam(required = false) QuoteStatus status,
        @RequestParam(required = false) String currency,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String ownerId = jwt.getSubject();
        PageResponse<QuoteResponse> result = quoteService.list(ownerId, leadId, status, currency, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuoteDetailResponse> getById(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.getById(ownerId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuoteDetailResponse> update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody QuoteUpdateRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.update(ownerId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        quoteService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<QuoteDetailResponse> addItem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody QuoteItemRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.addItem(ownerId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<QuoteDetailResponse> removeItem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @PathVariable Long itemId
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.removeItem(ownerId, id, itemId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<QuoteDetailResponse> accept(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteService.acceptQuote(ownerId, id);
        return ResponseEntity.ok(response);
    }
}
