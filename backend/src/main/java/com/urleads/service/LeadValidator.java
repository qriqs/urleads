package com.urleads.service;

import java.util.Locale;
import java.util.Set;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.LeadStage;
import com.urleads.exception.LeadValidationException;

public final class LeadValidator {

    public static final String DEFAULT_PRIORITY = "MEDIA";
    public static final String DEFAULT_ORIGIN = "MANUAL";

    public static final Set<String> VALID_PRIORITIES = Set.of("BAJA", "MEDIA", "ALTA");
    public static final Set<String> VALID_ORIGINS = Set.of("WEB", "REFERIDO", "MANUAL");

    private LeadValidator() {
    }

    public static void validate(CreateLeadRequest request) {
        validarContacto(request.getTelefono(), request.getCorreo());
        validarEtapa(request.getEtapa());
        validarPrioridad(request.getPriority(), request.isPrioritySupplied());
        validarOrigen(request.getOrigin(), request.isOriginSupplied());
    }

    public static void validate(UpdateLeadRequest request) {
        validarContacto(request.getTelefono(), request.getCorreo());
        validarEtapa(request.getEtapa());
        validarPrioridad(request.getPriority(), request.isPrioritySupplied());
        validarOrigen(request.getOrigin(), request.isOriginSupplied());
    }

    public static void validarEtapa(String etapa) {
        if (etapa == null || etapa.isBlank() || !LeadStage.isValid(etapa)) {
            throw new LeadValidationException("Etapa no válida: solo se permite NUEVO, EN_SEGUIMIENTO o CERRADO");
        }
    }

    public static boolean isValidPriority(String priority) {
        if (priority == null) {
            return false;
        }
        return VALID_PRIORITIES.contains(priority.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isValidOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        return VALID_ORIGINS.contains(origin.trim().toUpperCase(Locale.ROOT));
    }

    public static void validarPrioridad(String priority, boolean supplied) {
        if (supplied && priority == null) {
            throw new LeadValidationException("Prioridad no válida: no se permite valor nulo");
        }
        if (priority != null && (priority.isBlank() || !isValidPriority(priority))) {
            throw new LeadValidationException("Prioridad no válida: solo se permite BAJA, MEDIA o ALTA");
        }
    }

    public static void validarOrigen(String origin, boolean supplied) {
        if (supplied && origin == null) {
            throw new LeadValidationException("Origen no válido: no se permite valor nulo");
        }
        if (origin != null && (origin.isBlank() || !isValidOrigin(origin))) {
            throw new LeadValidationException("Origen no válido: solo se permite WEB, REFERIDO o MANUAL");
        }
    }

    private static void validarContacto(String telefono, String correo) {
        boolean telefonoVacio = telefono == null || telefono.isBlank();
        boolean correoVacio = correo == null || correo.isBlank();

        if (telefonoVacio && correoVacio) {
            throw new LeadValidationException("Debe registrar teléfono o correo");
        }
    }
}