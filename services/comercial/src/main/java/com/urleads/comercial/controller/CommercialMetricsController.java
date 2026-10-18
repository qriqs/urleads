package com.urleads.comercial.controller;

import com.urleads.comercial.dto.CommercialMetricsResponse;
import com.urleads.comercial.service.CommercialMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class CommercialMetricsController {

    private final CommercialMetricsService commercialMetricsService;

    public CommercialMetricsController(CommercialMetricsService commercialMetricsService) {
        this.commercialMetricsService = commercialMetricsService;
    }

    @GetMapping
    public ResponseEntity<CommercialMetricsResponse> getMetrics(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String currency
    ) {
        String ownerId = jwt.getSubject();
        CommercialMetricsResponse response = commercialMetricsService.getMetrics(ownerId, currency);
        return ResponseEntity.ok(response);
    }
}
