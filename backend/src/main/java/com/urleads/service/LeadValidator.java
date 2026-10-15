package com.urleads.service;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.LeadStage;
import com.urleads.exception.LeadValidationException;

public final class LeadValidator {

    private LeadValidator() {
    }

    public static void validate(CreateLeadRequest request) {
        validarContacto(request.getTelefono(), request.getCorreo());
        validarEtapa(request.getEtapa());
    }

    public static void validate(UpdateLeadRequest request) {
        validarContacto(request.getTelefono(), request.getCorreo());
        validarEtapa(request.getEtapa());
    }

    public static void validarEtapa(String etapa) {
        if (etapa == null || etapa.isBlank() || !LeadStage.isValid(etapa)) {
            throw new LeadValidationException("Etapa no válida: solo se permite NUEVO, EN_SEGUIMIENTO o CERRADO");
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