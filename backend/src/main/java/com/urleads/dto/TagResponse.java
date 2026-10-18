package com.urleads.dto;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TagResponse(
    @JsonProperty("id")
    Long id,
    @JsonProperty("name")
    String name,
    @JsonProperty("createdAt")
    OffsetDateTime createdAt
) {}
