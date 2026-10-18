package com.urleads.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload mínimo de datos sin información de identificación personal (PII)
 * para el evento lead.deleted.v1 (Backend V2 C04).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LeadDeletedEventData(
        @JsonProperty("leadId") Long leadId
) {
}
