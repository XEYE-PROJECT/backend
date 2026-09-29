package com.xeye.backend.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flyway aplica todas las migraciones sobre una MariaDB limpia y el esquema resultante es el esperado. */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertTrue(versions.size() >= 9, "expected the V1..V9 migrations, got " + versions);
        assertEquals("1", versions.get(0));
    }

    @Test
    void coreTablesExist() {
        for (String table : List.of("users", "api_keys", "lists", "elements", "trainings", "training_embeddings",
                "searches", "outbox_events", "revoked_tokens", "user_tokens")) {
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE 1 = 0",
                    Integer.class), "table " + table);
        }
    }

    @Test
    void apiKeysNeverStoreTheRawKey() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'api_keys'", String.class);
        assertTrue(columns.contains("key_hash"), columns.toString());
        assertTrue(columns.contains("key_prefix"), columns.toString());
        assertTrue(!columns.contains("api_key"), "api_keys.api_key (raw) must not exist: " + columns);
    }

    @Test
    void devAdminIsSeededAndVerified() {
        Boolean verified = jdbc.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class,
                ADMIN_EMAIL);
        assertEquals(Boolean.TRUE, verified);
    }
}
