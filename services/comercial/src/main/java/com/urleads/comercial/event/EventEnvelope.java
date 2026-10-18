package com.urleads.comercial.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int version,
    Instant occurredAt,
    String ownerId,
    T data
) {}
