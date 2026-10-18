package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * DTO para la respuesta interna de consulta de lead sin datos personales (PII) (Backend V2 C03).
 * Expone exclusivamente id, stage y nextFollowUp.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InternalLeadResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("stage") String stage,
        @JsonProperty("nextFollowUp") LocalDate nextFollowUp
) {
}
