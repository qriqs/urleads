package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteOutcomeReason;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record QuoteOutcomeRequest(
    @NotNull(message = "El motivo de resultado comercial es obligatorio")
    QuoteOutcomeReason reason,
    String notes,
    Instant date
) {}
