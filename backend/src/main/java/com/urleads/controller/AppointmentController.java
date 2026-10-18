package com.urleads.controller;

import com.urleads.dto.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CurrentUserProvider currentUserProvider;

    public AppointmentController(AppointmentService appointmentService, CurrentUserProvider currentUserProvider) {
        this.appointmentService = appointmentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        AppointmentResponse response = appointmentService.crearCita(usuarioId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppointmentResponse>> listAppointments(
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int validatedSize = Math.min(Math.max(1, size), 100);
        int validatedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Instant desdeInstant = (desde != null && !desde.isBlank())
                ? AppointmentService.parseIsoDateTime(desde, ZoneId.of("America/Lima"))
                : null;
        Instant hastaInstant = (hasta != null && !hasta.isBlank())
                ? AppointmentService.parseIsoDateTime(hasta, ZoneId.of("America/Lima"))
                : null;

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(PageResponse.from(
                appointmentService.obtenerCitas(usuarioId, leadId, estado, desdeInstant, hastaInstant, pageable)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(appointmentService.obtenerCitaPorId(id, usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(appointmentService.actualizarCita(id, usuarioId, request, token));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AppointmentResponse> updateAppointmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(appointmentService.actualizarEstadoCita(id, usuarioId, request.getEstado()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppointment(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        appointmentService.eliminarCita(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
