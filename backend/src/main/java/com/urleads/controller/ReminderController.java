package com.urleads.controller;

import com.urleads.dto.CreateReminderRequest;
import com.urleads.dto.PageResponse;
import com.urleads.dto.ReminderResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;
    private final CurrentUserProvider currentUserProvider;

    public ReminderController(ReminderService reminderService, CurrentUserProvider currentUserProvider) {
        this.reminderService = reminderService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> createReminder(@Valid @RequestBody CreateReminderRequest request) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        ReminderResponse response = reminderService.programarRecordatorio(usuarioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReminderResponse>> listReminders(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int validatedSize = Math.min(Math.max(1, size), 100);
        int validatedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(PageResponse.from(
                reminderService.obtenerRecordatoriosUsuario(usuarioId, estado, pageable)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReminder(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        reminderService.cancelarRecordatorio(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPendingReminders() {
        int procesados = reminderService.procesarRecordatoriosPendientes(Instant.now());
        return ResponseEntity.ok(Map.of("procesados", procesados, "timestamp", Instant.now().toString()));
    }
}
