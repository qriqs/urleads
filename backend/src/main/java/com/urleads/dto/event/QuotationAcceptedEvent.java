package com.urleads.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationAcceptedEvent {
    private String eventId;
    private String eventType;
    private Integer version;
    private Instant occurredAt;
    private String ownerId;
    private QuotationAcceptedData data;
}
