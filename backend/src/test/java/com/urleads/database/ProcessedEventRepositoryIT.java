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

        // Intento de duplicado debe lanzar violación de integridad
        ProcessedEvent duplicateEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("quotation.accepted.v1")
                .processedAt(Instant.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            processedEventRepository.saveAndFlush(duplicateEvent);
        });
    }
}
