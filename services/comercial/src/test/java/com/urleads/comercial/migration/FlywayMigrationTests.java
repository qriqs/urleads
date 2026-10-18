package com.urleads.comercial.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway migration executes successfully and applies V1")
    void flywayMigrationApplied() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("Comercial tables exist and can be queried in PostgreSQL")
    void tablesExist() {
        Integer catalogCount = jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_items", Integer.class);
        Integer quotesCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quotes", Integer.class);
        Integer itemsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quote_items", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);

        assertThat(catalogCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(quotesCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(itemsCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(outboxCount).isNotNull().isGreaterThanOrEqualTo(0);
    }
}
