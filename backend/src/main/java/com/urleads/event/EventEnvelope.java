package com.urleads.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sobre estándar canónico de eventos de dominio en urleads.events.v1 (Backend V2 C04).
 *
 * @param <T> tipo del payload de datos mínimos sin PII
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EventEnvelope<T>(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("occurredAt") String occurredAt,
        @JsonProperty("ownerId") String ownerId,
        @JsonProperty("data") T data
) {
}
