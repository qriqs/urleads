package com.urleads.comercial.client.dto;

import java.time.LocalDate;

public record LeadInternalResponse(
    Long id,
    String stage,
    LocalDate nextFollowUp
) {}
