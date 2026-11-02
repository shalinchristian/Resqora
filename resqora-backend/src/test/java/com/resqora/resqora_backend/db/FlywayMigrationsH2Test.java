package com.resqora.resqora_backend.db;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs every real Flyway migration (V1..V10) on an empty H2 database in PostgreSQL mode
class FlywayMigrationsH2Test {
    @Test
    void allMigrationsRunAndV10AddsTheNewColumnsWithSafeDefaults() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway-check;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        int applied = flyway.migrate().migrationsExecuted;
        assertTrue(applied >= 10, "migrations run: " + applied);
        assertEquals("10", flyway.info().current().getVersion().getVersion());

        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("INSERT INTO users (email, password_hash, name) VALUES ('a@example.com', 'hash', 'A')");
            try (ResultSet row = sql.executeQuery("SELECT ai_enabled FROM users WHERE email = 'a@example.com'")) {
                assertTrue(row.next());
                assertTrue(row.getBoolean(1)); // existing and new accounts start with AI on
            }
            sql.execute("INSERT INTO ai_usage (user_id, created_at) SELECT id, CURRENT_TIMESTAMP FROM users");
            try (ResultSet row = sql.executeQuery("SELECT kind, provider FROM ai_usage")) {
                assertTrue(row.next());
                assertEquals("CHAT", row.getString(1)); // older rows count as Gemini chat
                assertEquals("GEMINI", row.getString(2));
            }
            try (ResultSet column = sql.executeQuery("SELECT column_default FROM information_schema.columns "
                    + "WHERE table_name = 'resume_analyses' AND column_name = 'analyzer_version'")) {
                assertTrue(column.next());
                assertEquals("1", column.getString(1)); // older analyses are recomputed by the new analyzer
            }
        }
    }
}
