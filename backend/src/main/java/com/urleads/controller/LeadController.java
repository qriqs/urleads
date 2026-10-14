package com.urleads.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.urleads.service.LeadService;

import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.UpdateLeadRequest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

	private final LeadService leadService;

	@GetMapping
	public List<LeadResponse> listar(@RequestParam Long usuarioId) {
		/*
		 * Bloqueado por integración con módulo de autenticación. El controlador
		 * continúa usando usuarioId temporalmente hasta disponer de identidad
		 * autenticada provista por Spring Security.
		 */

		return leadService.listar(usuarioId);
	}

	// Obtener
	@GetMapping("/{id}")
	public LeadResponse obtener(@PathVariable Long id, @RequestParam Long usuarioId) {

		return leadService.obtener(id, usuarioId);
	}

	// Crear
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LeadResponse crear(@RequestParam Long usuarioId, @Valid @RequestBody CreateLeadRequest request) {

		return leadService.crear(usuarioId, request);
	}

	// Actualizar
	@PutMapping("/{id}")
	public LeadResponse actualizar(@PathVariable Long id, @RequestParam Long usuarioId,
			@Valid @RequestBody UpdateLeadRequest request) {

		return leadService.actualizar(id, usuarioId, request);
	}

	// Eliminar
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id, @RequestParam Long usuarioId) {

		leadService.eliminar(id, usuarioId);
	}

}