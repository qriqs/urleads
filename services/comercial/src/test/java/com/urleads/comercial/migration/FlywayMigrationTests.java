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
    @DisplayName("Flyway migration executes successfully and applies migrations")
    void flywayMigrationApplied() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
    }

    @Test
    @DisplayName("Comercial tables exist and can be queried in PostgreSQL")
    void tablesExist() {
        Integer catalogCount = jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_items", Integer.class);
        Integer quotesCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quotes", Integer.class);
        Integer itemsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quote_items", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        Integer templatesCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quote_templates", Integer.class);
        Integer templateItemsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quote_template_items", Integer.class);
        Integer ordersCount = jdbcTemplate.queryForObject("SELECT count(*) FROM commercial_orders", Integer.class);
        Integer orderItemsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM commercial_order_items", Integer.class);
        Integer outcomeColumnsCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'quotes' AND column_name IN ('outcome_reason', 'outcome_notes', 'outcome_date')",
            Integer.class
        );
        Integer revisionColumnsCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'quotes' AND column_name IN ('version', 'root_quote_id', 'parent_quote_id', 'revision_number', 'is_latest')",
            Integer.class
        );

        assertThat(catalogCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(quotesCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(itemsCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(outboxCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(templatesCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(templateItemsCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(ordersCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(orderItemsCount).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(outcomeColumnsCount).isEqualTo(3);
        assertThat(revisionColumnsCount).isEqualTo(5);
    }
}
