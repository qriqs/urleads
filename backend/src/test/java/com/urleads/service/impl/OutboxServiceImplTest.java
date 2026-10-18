package com.urleads.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.urleads.entity.Lead;
import com.urleads.entity.OutboxEvent;
import com.urleads.entity.OutboxStatus;
import com.urleads.entity.Usuario;
import com.urleads.event.CrmEventTypes;
import com.urleads.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("R1/R2: Pruebas unitarias de OutboxServiceImpl")
class OutboxServiceImplTest {

    @Mock
    private OutboxRepository outboxRepository;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        fixedClock = Clock.fixed(Instant.parse("2026-10-17T23:30:00Z"), ZoneOffset.UTC);
        outboxService = new OutboxServiceImpl(outboxRepository, objectMapper, fixedClock);
    }

    @Test
    @DisplayName("recordLeadCreated: persiste registro outbox con sobre canónico y payload correcto")
    void recordLeadCreated_persistsExpectedOutboxEvent() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(42L);

        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(usuario);
        lead.setNombre("Lead Seguro");
        lead.setTelefono("+51999888777");
        lead.setCorreo("seguro@example.com");
        lead.setEtapa("NUEVO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 10, 25));

        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        OutboxEvent result = outboxService.recordLeadCreated(42L, lead);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isNotBlank();
        assertThat(saved.getEventType()).isEqualTo(CrmEventTypes.LEAD_CREATED);
        assertThat(saved.getRoutingKey()).isEqualTo("lead.created.v1");
        assertThat(saved.getExchange()).isEqualTo("urleads.events.v1");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getLastError()).isNull();

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventId").asText()).isEqualTo(saved.getEventId());
        assertThat(payload.get("eventType").asText()).isEqualTo("lead.created.v1");
        assertThat(payload.get("version").asInt()).isEqualTo(1);
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-10-17T23:30:00Z");
        assertThat(payload.get("ownerId").asText()).isEqualTo("42");

        JsonNode data = payload.get("data");
        assertThat(data.get("leadId").asLong()).isEqualTo(105L);
        assertThat(data.get("stage").asText()).isEqualTo("NUEVO");
        assertThat(data.get("nextFollowUp").asText()).isEqualTo("2026-10-25");
        assertThat(data.has("nombre")).isFalse();
    }

    @Test
    @DisplayName("recordLeadUpdated: persiste registro outbox para lead.updated.v1")
    void recordLeadUpdated_persistsExpectedOutboxEvent() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(42L);

        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(usuario);
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 10, 28));

        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.recordLeadUpdated(42L, lead);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(CrmEventTypes.LEAD_UPDATED);
        assertThat(saved.getRoutingKey()).isEqualTo("lead.updated.v1");

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("lead.updated.v1");
        assertThat(payload.get("data").get("stage").asText()).isEqualTo("EN_SEGUIMIENTO");
    }

    @Test
    @DisplayName("recordLeadDeleted: persiste registro outbox para lead.deleted.v1 con solo leadId")
    void recordLeadDeleted_persistsExpectedOutboxEvent() throws Exception {
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.recordLeadDeleted(42L, 105L);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(CrmEventTypes.LEAD_DELETED);
        assertThat(saved.getRoutingKey()).isEqualTo("lead.deleted.v1");

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("lead.deleted.v1");
        assertThat(payload.get("data").size()).isEqualTo(1);
        assertThat(payload.get("data").get("leadId").asLong()).isEqualTo(105L);
    }
}
