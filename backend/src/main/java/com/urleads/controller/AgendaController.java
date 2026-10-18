package com.urleads.controller;

import com.urleads.dto.AgendaItemResponse;
import com.urleads.dto.DailyPendingResponse;
import com.urleads.dto.PageResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.AgendaQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agenda")
public class AgendaController {

    private final AgendaQueryService agendaQueryService;
    private final CurrentUserProvider currentUserProvider;

    public AgendaController(AgendaQueryService agendaQueryService, CurrentUserProvider currentUserProvider) {
        this.agendaQueryService = agendaQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AgendaItemResponse>> getAgenda(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int validatedSize = Math.min(Math.max(1, size), 100);
        int validatedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(PageResponse.from(
                agendaQueryService.obtenerAgendaCombinada(usuarioId, desde, hasta, leadId, tipo, pageable)
        ));
    }

    @GetMapping("/daily-pending")
    public ResponseEntity<DailyPendingResponse> getDailyPending(
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String zonaHoraria
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(agendaQueryService.obtenerResumenPendientesDelDia(usuarioId, fecha, zonaHoraria));
    }
}
