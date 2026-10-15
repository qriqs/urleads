package com.urleads.controller;

import com.urleads.dto.NotaRequest;
import com.urleads.dto.NotaResponse;
import com.urleads.service.NotaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads/{id}/notes")
public class NotaController {

    private final NotaService notaService;

    public NotaController(NotaService notaService) {
        this.notaService = notaService;
    }

    @GetMapping
    public ResponseEntity<List<NotaResponse>> listarNotas(
            @PathVariable("id") Long leadId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<NotaResponse> notas = notaService.listarNotas(leadId, userDetails.getUsername());
        return ResponseEntity.ok(notas);
    }

    @PostMapping
    public ResponseEntity<NotaResponse> crearNota(
            @PathVariable("id") Long leadId,
            @Valid @RequestBody NotaRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        NotaResponse creada = notaService.crearNota(leadId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}