package com.resqora.resqora_backend.db;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Runs every real Flyway migration (V1..V14) on an empty H2 database in PostgreSQL mode
class FlywayMigrationsH2Test {
    @Test
    void allMigrationsRunWithSafeDefaults() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway-check;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        int applied = flyway.migrate().migrationsExecuted;
        assertTrue(applied >= 14, "migrations run: " + applied);
        assertEquals("14", flyway.info().current().getVersion().getVersion());

        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("INSERT INTO users (email, password_hash, name) VALUES ('a@example.com', 'hash', 'A')");
            try (ResultSet row = sql.executeQuery("SELECT ai_enabled FROM users WHERE email = 'a@example.com'")) {
                assertTrue(row.next());
                assertTrue(row.getBoolean(1)); // existing and new accounts start with AI on
            }
            sql.execute("INSERT INTO ai_usage (user_id, created_at) SELECT id, CURRENT_TIMESTAMP FROM users");
            try (ResultSet row = sql.executeQuery("SELECT kind, provider FROM ai_usage")) {
                assertTrue(row.next());
                assertEquals("RESUME_CHAT", row.getString(1)); // a row without a kind counts as resume chat
                assertEquals("GEMINI", row.getString(2));
            }
            try (ResultSet column = sql.executeQuery("SELECT column_default FROM information_schema.columns "
                    + "WHERE table_name = 'resume_analyses' AND column_name = 'analyzer_version'")) {
                assertTrue(column.next());
                assertEquals("1", column.getString(1)); // older analyses are recomputed by the new analyzer
            }
            // V11: an account inserted without the new columns is active, with its email as active_email
            sql.execute("UPDATE users SET active_email = email WHERE email = 'a@example.com'");
            try (ResultSet row = sql.executeQuery("SELECT status, deleted_at, created_at FROM users WHERE email = 'a@example.com'")) {
                assertTrue(row.next());
                assertEquals("ACTIVE", row.getString(1));
                assertNull(row.getTimestamp(2));
                assertNotNull(row.getTimestamp(3));
            }
            // two deleted accounts (active_email NULL) are allowed, a second active one is not
            sql.execute("INSERT INTO users (email, password_hash, status, active_email) VALUES ('b@example.com', 'x', 'DELETED', NULL)");
            sql.execute("INSERT INTO users (email, password_hash, status, active_email) VALUES ('c@example.com', 'x', 'DELETED', NULL)");
            sql.execute("INSERT INTO users (email, password_hash, active_email) VALUES ('d@example.com', 'x', 'd@example.com')");
            assertThrows(java.sql.SQLException.class, () -> sql.execute(
                    "INSERT INTO users (email, password_hash, active_email) VALUES ('d2@example.com', 'x', 'd@example.com')"));
        }
    }

    // V13 splits old shared CHAT rows into the two chat buckets and leaves summaries alone
    @Test
    void v13ClassifiesExistingChatRows() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway-v13;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("12").load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("INSERT INTO users (email, password_hash, name) VALUES ('a@example.com', 'hash', 'A')");
            for (String row : new String[] {"'CHAT', 'GEMINI'", "'CHAT', 'GROQ'", "'SUMMARY', 'GEMINI'"}) {
                sql.execute("INSERT INTO ai_usage (user_id, kind, provider, created_at) SELECT id, " + row
                        + ", CURRENT_TIMESTAMP FROM users");
            }
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("SELECT kind, provider FROM ai_usage ORDER BY id")) {
            String[][] expected = {{"RESUME_CHAT", "GEMINI"}, {"DASHBOARD_CHAT", "GROQ"}, {"SUMMARY", "GEMINI"}};
            for (String[] row : expected) {
                assertTrue(rows.next());
                assertEquals(row[0], rows.getString(1));
                assertEquals(row[1], rows.getString(2));
            }
        }
    }
}
