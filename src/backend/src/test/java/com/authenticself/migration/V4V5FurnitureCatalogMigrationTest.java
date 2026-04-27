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
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for V4 + V5 Flyway migrations
 * (UC-01-recommendation AC-24 / AC-25 / AC-26 / AC-52).
 * <p>
 * Spins up MySQL 8 via Testcontainers, runs Flyway through the full
 * migration chain, and asserts the schema + seed data exercise every
 * scorer branch.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4V5FurnitureCatalogMigrationTest {

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
    // AC-24 — V4 adds the eight new columns.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-24: V4 adds eight columns to furniture")
    void ac24_v4Columns() throws SQLException {
        String[] expected = {
                "name", "price", "image_url", "color_hex",
                "width_cm", "length_cm", "height_cm", "style_tags"
        };
        for (String col : expected) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM information_schema.columns " +
                         "WHERE table_schema=? AND table_name='furniture' AND column_name=?")) {
                ps.setString(1, DB_NAME);
                ps.setString(2, col);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("furniture.%s must exist after V4", col)
                            .isTrue();
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-25 — V5 seeds >= 24 rows, >= 6 per category
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-25: furniture has >=24 rows with >=6 per type")
    void ac25_seedCounts() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM furniture")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(24);
            }

            Map<String, Integer> counts = new HashMap<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT type, COUNT(*) FROM furniture GROUP BY type")) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getInt(2));
                }
            }
            for (String cat : new String[] { "desk", "bed", "chair", "lighting" }) {
                assertThat(counts.getOrDefault(cat, 0))
                        .as("type=%s must have >=6 rows", cat)
                        .isGreaterThanOrEqualTo(6);
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-26a — oversize row per category
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-26a: each category has at least one oversize row (max(w,l) > 400)")
    void ac26_oversize() throws SQLException {
        for (String cat : new String[] { "desk", "bed", "chair", "lighting" }) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT COUNT(*) FROM furniture " +
                         "WHERE type=? AND GREATEST(width_cm, length_cm) > 400")) {
                ps.setString(1, cat);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("type=%s must have >=1 oversize row", cat)
                            .isGreaterThanOrEqualTo(1);
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-26b — every PreferredStyle enum (except CURRENT) appears in at
    // least one row's style_tags.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-26b: every AI-output style value appears in at least one row's style_tags")
    void ac26_styleCoverage() throws SQLException {
        for (String style : new String[] { "MODERN", "SIMPLE", "CLASSIC", "SCANDINAVIAN", "INDUSTRIAL" }) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT COUNT(*) FROM furniture WHERE FIND_IN_SET(?, style_tags) > 0")) {
                ps.setString(1, style);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("style=%s must appear in at least one row's style_tags", style)
                            .isGreaterThanOrEqualTo(1);
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // flyway_schema_history has both V4 and V5 rows with success=1.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("flyway_schema_history carries success=1 for versions 4 and 5")
    void historyRowsPresent() throws SQLException {
        for (String v : new String[] { "4", "5" }) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT success FROM flyway_schema_history WHERE version=?")) {
                ps.setString(1, v);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("version %s row", v).isTrue();
                    assertThat(rs.getBoolean(1)).isTrue();
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-52 — V4/V5 do not regress V1/V2/V3 columns on spaces.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-52: V1/V2/V3 columns on spaces still intact")
    void ac52_noRegression() throws SQLException {
        for (String col : new String[] {
                "room_id", "user_id", "photo_url", "original_filename",
                "content_type", "file_size_bytes", "status", "uploaded_at",
                "dimensions", "main_color", "style", "analysis_date",
                "preferred_style"
        }) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM information_schema.columns " +
                         "WHERE table_schema=? AND table_name='spaces' AND column_name=?")) {
                ps.setString(1, DB_NAME);
                ps.setString(2, col);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("spaces.%s must still exist after V4/V5", col)
                            .isTrue();
                }
            }
        }
    }
}
