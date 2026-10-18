package com.urleads.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record UpdateLeadTagsRequest(
    @NotNull(message = "tagIds no puede ser nulo")
    @JsonProperty("tagIds")
    List<Long> tagIds
) {}
