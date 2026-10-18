package com.urleads.comercial.controller;

import com.urleads.comercial.dto.CommercialOrderDetailResponse;
import com.urleads.comercial.dto.CommercialOrderResponse;
import com.urleads.comercial.dto.PageResponse;
import com.urleads.comercial.service.CommercialOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class CommercialOrderController {

    private final CommercialOrderService commercialOrderService;

    public CommercialOrderController(CommercialOrderService commercialOrderService) {
        this.commercialOrderService = commercialOrderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommercialOrderDetailResponse> getById(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String ownerId = jwt.getSubject();
        CommercialOrderDetailResponse response = commercialOrderService.getById(ownerId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<CommercialOrderResponse>> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String ownerId = jwt.getSubject();
        PageResponse<CommercialOrderResponse> response = commercialOrderService.list(ownerId, page, size);
        return ResponseEntity.ok(response);
    }
}
