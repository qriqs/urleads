package com.urleads.dto;

import java.time.OffsetDateTime;

public record NotaResponse(
    Long id,
    String content,
    OffsetDateTime createdAt
) {}