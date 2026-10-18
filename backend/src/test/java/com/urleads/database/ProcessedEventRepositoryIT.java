package com.urleads.database;

import com.urleads.entity.ProcessedEvent;
import com.urleads.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
@Tag("db-integration")
@ActiveProfiles("db-integration")
class ProcessedEventRepositoryIT {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deduplicación en BD: existe por eventId y previene duplicados en clave primaria")
    void deduplication_persistsAndEnforcesUniqueEventId() {
        String eventId = "evt-it-unique-001";

        assertThat(processedEventRepository.existsByEventId(eventId)).isFalse();

        ProcessedEvent event1 = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("quotation.accepted.v1")
                .processedAt(Instant.now())
                .build();
        processedEventRepository.saveAndFlush(event1);

        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();

        // Intento de duplicado en BD debe lanzar violación de clave primaria / integridad
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO processed_event (event_id, event_type, processed_at) VALUES (?, ?, ?)",
                    eventId, "quotation.accepted.v1", java.sql.Timestamp.from(Instant.now()));
        });
    }
}
