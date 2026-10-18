package com.urleads.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("R2: Pruebas de contrato y serialización de eventos de dominio CRM")
class LeadEventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("lead.created.v1: Sobre canónico con campos exactos, ownerId string y data mínima sin PII")
    void leadCreatedEvent_serializesCanonicalEnvelopeWithoutPii() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-10-17T23:30:00Z";
        String ownerId = "42";

        LeadEventData data = new LeadEventData(105L, "NUEVO", LocalDate.of(2026, 10, 25));
        EventEnvelope<LeadEventData> envelope = new EventEnvelope<>(
                eventId,
                CrmEventTypes.LEAD_CREATED,
                1,
                occurredAt,
                ownerId,
                data
        );

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode rootNode = objectMapper.readTree(json);

        // Validar sobre canónico
        assertThat(rootNode.get("eventId").asText()).isEqualTo(eventId);
        assertThat(rootNode.get("eventType").asText()).isEqualTo("lead.created.v1");
        assertThat(rootNode.get("version").asInt()).isEqualTo(1);
        assertThat(rootNode.get("occurredAt").asText()).isEqualTo("2026-10-17T23:30:00Z");
        assertThat(rootNode.get("ownerId").asText()).isEqualTo("42");
        assertThat(rootNode.get("ownerId").isTextual()).isTrue();

        // Validar data
        JsonNode dataNode = rootNode.get("data");
        assertThat(dataNode.get("leadId").asLong()).isEqualTo(105L);
        assertThat(dataNode.get("stage").asText()).isEqualTo("NUEVO");
        assertThat(dataNode.get("nextFollowUp").asText()).isEqualTo("2026-10-25");

        // Regla estricta de no-PII: no nombres, teléfonos, correos, servicios ni notas
        assertThat(dataNode.has("nombre")).isFalse();
        assertThat(dataNode.has("telefono")).isFalse();
        assertThat(dataNode.has("correo")).isFalse();
        assertThat(dataNode.has("service")).isFalse();
        assertThat(dataNode.has("notas")).isFalse();
        assertThat(dataNode.has("contenido")).isFalse();
    }

    @Test
    @DisplayName("lead.updated.v1: Con nextFollowUp null serializa explícitamente como null JSON")
    void leadUpdatedEvent_withNullNextFollowUp_serializesExplicitJsonNull() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-10-17T23:35:00Z";
        String ownerId = "42";

        LeadEventData data = new LeadEventData(105L, "CERRADO", null);
        EventEnvelope<LeadEventData> envelope = new EventEnvelope<>(
                eventId,
                CrmEventTypes.LEAD_UPDATED,
                1,
                occurredAt,
                ownerId,
                data
        );

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode rootNode = objectMapper.readTree(json);

        assertThat(rootNode.get("eventType").asText()).isEqualTo("lead.updated.v1");
        JsonNode dataNode = rootNode.get("data");
        assertThat(dataNode.get("leadId").asLong()).isEqualTo(105L);
        assertThat(dataNode.get("stage").asText()).isEqualTo("CERRADO");
        assertThat(dataNode.has("nextFollowUp")).isTrue();
        assertThat(dataNode.get("nextFollowUp").isNull()).isTrue();
    }

    @Test
    @DisplayName("lead.deleted.v1: data contiene exclusivamente leadId numérico")
    void leadDeletedEvent_dataContainsOnlyLeadId() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-10-17T23:40:00Z";
        String ownerId = "99";

        LeadDeletedEventData data = new LeadDeletedEventData(200L);
        EventEnvelope<LeadDeletedEventData> envelope = new EventEnvelope<>(
                eventId,
                CrmEventTypes.LEAD_DELETED,
                1,
                occurredAt,
                ownerId,
                data
        );

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode rootNode = objectMapper.readTree(json);

        assertThat(rootNode.get("eventId").asText()).isEqualTo(eventId);
        assertThat(rootNode.get("eventType").asText()).isEqualTo("lead.deleted.v1");
        assertThat(rootNode.get("version").asInt()).isEqualTo(1);
        assertThat(rootNode.get("ownerId").asText()).isEqualTo("99");

        JsonNode dataNode = rootNode.get("data");
        assertThat(dataNode.size()).isEqualTo(1);
        assertThat(dataNode.get("leadId").asLong()).isEqualTo(200L);
    }
}
