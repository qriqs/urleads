package com.urleads.controller;

import com.urleads.dto.UserPreferencesRequest;
import com.urleads.dto.UserPreferencesResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.UserPreferencesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;
    private final CurrentUserProvider currentUserProvider;

    public UserPreferencesController(
            UserPreferencesService userPreferencesService,
            CurrentUserProvider currentUserProvider
    ) {
        this.userPreferencesService = userPreferencesService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<UserPreferencesResponse> getPreferences() {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userPreferencesService.obtenerPreferencias(usuarioId));
    }

    @PutMapping
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @Valid @RequestBody UserPreferencesRequest request
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userPreferencesService.actualizarPreferencias(usuarioId, request));
    }
}
