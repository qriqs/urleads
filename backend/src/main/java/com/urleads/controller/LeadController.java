package com.urleads.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public PageResponse<LeadResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String followUp,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false, defaultValue = "0") int page) {

        if (priority == null && origin == null) {
            return leadService.listarPaginado(
                    currentUserProvider.getCurrentUserId(),
                    search,
                    followUp,
                    stage,
                    page);
        }

        return leadService.listarPaginado(
                currentUserProvider.getCurrentUserId(),
                search,
                followUp,
                stage,
                priority,
                origin,
                page);
    }

    @GetMapping("/{id}")
    public LeadResponse obtener(@PathVariable Long id) {

        return leadService.obtener(
                id,
                currentUserProvider.getCurrentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse crear(@Valid @RequestBody CreateLeadRequest request) {

        return leadService.crear(
                currentUserProvider.getCurrentUserId(),
                request);
    }

    @PutMapping("/{id}")
    public LeadResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLeadRequest request) {

        return leadService.actualizar(
                id,
                currentUserProvider.getCurrentUserId(),
                request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {

        leadService.eliminar(
                id,
                currentUserProvider.getCurrentUserId());
    }
}