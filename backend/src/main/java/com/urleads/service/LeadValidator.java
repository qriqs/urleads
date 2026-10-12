package com.urleads.service;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.exception.LeadValidationException;

public class LeadValidator {

    private LeadValidator() {
    }

    public static void validate(
            CreateLeadRequest request) {

        validarContacto(
                request.getTelefono(),
                request.getCorreo());

        validarEtapa(
                request.getEtapa(),
                request.getProximoSeguimiento());
    }

    public static void validate(
            UpdateLeadRequest request) {

        validarContacto(
                request.getTelefono(),
                request.getCorreo());

        validarEtapa(
                request.getEtapa(),
                request.getProximoSeguimiento());
    }

    private static void validarContacto(
            String telefono,
            String correo) {

        boolean telefonoVacio =
                telefono == null || telefono.isBlank();

        boolean correoVacio =
                correo == null || correo.isBlank();

        if (telefonoVacio && correoVacio) {
            throw new LeadValidationException(
                    "Debe informar teléfono o correo.");
        }
    }

    private static void validarEtapa(
            String etapa,
            java.time.LocalDate proximoSeguimiento) {

        if ("CERRADO".equals(etapa)
                && proximoSeguimiento != null) {

            throw new LeadValidationException(
                    "Un lead cerrado no puede tener seguimiento.");
        }
    }
}