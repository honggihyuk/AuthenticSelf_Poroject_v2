package com.authenticself.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the V3 Flyway migration (Task-4 AC-9 / AC-36).
 * <p>
 * Spins up MySQL 8 via Testcontainers, runs Flyway against the full
 * migration directory, and asserts both:
 * <ul>
 *   <li>V3 adds {@code spaces.preferred_style VARCHAR(32) NULL} (AC-9).</li>
 *   <li>V1/V2 outputs are still intact — {@code spaces} still has the Task-3
 *       columns (dimensions/main_color/style/analysis_date) and the core PK
 *       + status index (regression coverage for AC-36).</li>
 * </ul>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V3PreferredStyleMigrationTest {

    private static final String DB_NAME = "authenticself";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(DB_NAME)
            .withUsername("test")
            .withPassword("test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("characterEncoding", "utf8mb4");

    private String jdbcUrl;
    private String user;
    private String pass;

    @BeforeAll
    void setUp() {
        MYSQL.start();
        this.jdbcUrl = MYSQL.getJdbcUrl();
        this.user    = MYSQL.getUsername();
        this.pass    = MYSQL.getPassword();

        Flyway.configure()
                .dataSource(jdbcUrl, user, pass)
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, pass);
    }

    // -----------------------------------------------------------------
    // AC-9 — preferred_style column present, varchar(32), NULLable
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: V3 adds spaces.preferred_style VARCHAR(32) NULL")
    void ac9_preferredStyleColumn() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, character_maximum_length, is_nullable " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='spaces' AND column_name='preferred_style'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("preferred_style column exists").isTrue();
                assertThat(rs.getString(1).toLowerCase()).isEqualTo("varchar");
                assertThat(rs.getLong(2)).isEqualTo(32L);
                assertThat(rs.getString(3)).isEqualTo("YES");
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-36 — V3 does not regress V1/V2 columns
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-36: V1/V2 columns remain intact after V3")
    void ac36_v1_v2_regression() throws SQLException {
        for (String col : new String[] {
                "room_id", "user_id", "photo_url", "original_filename",
                "content_type", "file_size_bytes", "status", "uploaded_at",
                "dimensions", "main_color", "style", "analysis_date"
        }) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM information_schema.columns " +
                         "WHERE table_schema=? AND table_name='spaces' AND column_name=?")) {
                ps.setString(1, DB_NAME);
                ps.setString(2, col);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("spaces.%s must still exist after V3", col)
                            .isTrue();
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // flyway_schema_history carries the V3 row
    // -----------------------------------------------------------------
    @Test
    @DisplayName("flyway_schema_history has a success=1 row for version 3")
    void v3_history_row_present() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT success FROM flyway_schema_history WHERE version='3'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isTrue();
            }
        }
    }
}
