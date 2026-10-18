package com.urleads.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Payload mínimo de datos sin información de identificación personal (PII)
 * para los eventos lead.created.v1 y lead.updated.v1 (Backend V2 C04).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LeadEventData(
        @JsonProperty("leadId") Long leadId,
        @JsonProperty("stage") String stage,
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty("nextFollowUp") LocalDate nextFollowUp
) {
}
