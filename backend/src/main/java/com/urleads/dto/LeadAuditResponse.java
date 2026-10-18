package com.urleads.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeadAuditResponse(
        Long id,
        Long leadId,
        @JsonProperty("field")
        String field,
        @JsonProperty("oldValue")
        String oldValue,
        @JsonProperty("newValue")
        String newValue,
        @JsonProperty("createdAt")
        OffsetDateTime createdAt) {
}
