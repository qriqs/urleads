package com.urleads.comercial.controller;

import com.urleads.comercial.dto.InstantiateQuoteRequest;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.dto.QuoteDetailResponse;
import com.urleads.comercial.dto.QuoteTemplateDetailResponse;
import com.urleads.comercial.dto.QuoteTemplateRequest;
import com.urleads.comercial.dto.QuoteTemplateResponse;
import com.urleads.comercial.service.QuoteTemplateService;
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
@RequestMapping("/api/v1/quote-templates")
public class QuoteTemplateController {

    private final QuoteTemplateService quoteTemplateService;

    public QuoteTemplateController(QuoteTemplateService quoteTemplateService) {
        this.quoteTemplateService = quoteTemplateService;
    }

    @PostMapping
    public ResponseEntity<QuoteTemplateDetailResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody QuoteTemplateRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteTemplateDetailResponse response = quoteTemplateService.create(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<QuoteTemplateResponse>> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String ownerId = jwt.getSubject();
        PageResponse<QuoteTemplateResponse> response = quoteTemplateService.list(ownerId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuoteTemplateDetailResponse> getById(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        QuoteTemplateDetailResponse response = quoteTemplateService.getById(ownerId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuoteTemplateDetailResponse> update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody QuoteTemplateRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteTemplateDetailResponse response = quoteTemplateService.update(ownerId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        quoteTemplateService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/create-quote")
    public ResponseEntity<QuoteDetailResponse> createQuoteFromTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody InstantiateQuoteRequest request
    ) {
        String ownerId = jwt.getSubject();
        QuoteDetailResponse response = quoteTemplateService.instantiateQuote(ownerId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/instantiate")
    public ResponseEntity<QuoteDetailResponse> instantiate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody InstantiateQuoteRequest request
    ) {
        return createQuoteFromTemplate(jwt, id, request);
    }
}
