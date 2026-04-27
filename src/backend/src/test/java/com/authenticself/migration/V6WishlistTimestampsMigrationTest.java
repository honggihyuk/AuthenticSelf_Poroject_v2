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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the V6 Flyway migration (UC-02-wishlist
 * AC-1 / AC-2 / AC-3 / AC-4).
 * <p>
 * Spins up MySQL 8 via Testcontainers, runs Flyway through V1..V6, and
 * asserts:
 * <ol>
 *   <li>{@code wishlist.added_at} + {@code purchased_at} columns have the
 *       correct types and nullability (AC-2).</li>
 *   <li>{@code uq_wishlist_user_furniture} UNIQUE index is present on
 *       columns {@code (user_id, furniture_id)} (AC-2).</li>
 *   <li>Inserting two rows with the same {@code (user_id, furniture_id)}
 *       tuple fails with a MySQL duplicate-key error (AC-3).</li>
 *   <li>V1..V5 migration rows in {@code flyway_schema_history} still
 *       carry {@code success=1} (AC-4).</li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V6WishlistTimestampsMigrationTest {

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
    void setUp() throws SQLException {
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

        seedUserAndFurniture();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, pass);
    }

    private void seedUserAndFurniture() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email) " +
                    "VALUES ('u1', 'Alice', 'alice@example.com')");
            // V5 already seeds the furniture catalog with 24+ rows — we
            // just need a valid furniture_id to reference. Pick one we
            // know exists across seed variants.
        }
    }

    private String anyFurnitureId() throws SQLException {
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT furniture_id FROM furniture LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    // -----------------------------------------------------------------
    // AC-2 — added_at / purchased_at columns present with correct types.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-2: wishlist.added_at is DATETIME NOT NULL with CURRENT_TIMESTAMP default")
    void ac2_addedAtColumn() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='wishlist' AND column_name='added_at'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualToIgnoringCase("datetime");
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("NO");
                // MySQL reports the default as "CURRENT_TIMESTAMP"
                assertThat(rs.getString("column_default"))
                        .containsIgnoringCase("CURRENT_TIMESTAMP");
            }
        }
    }

    @Test
    @DisplayName("AC-2: wishlist.purchased_at is DATETIME NULL")
    void ac2_purchasedAtColumn() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, is_nullable " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='wishlist' AND column_name='purchased_at'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualToIgnoringCase("datetime");
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("YES");
            }
        }
    }

    @Test
    @DisplayName("AC-2: uq_wishlist_user_furniture UNIQUE index on (user_id, furniture_id)")
    void ac2_uniqueIndex() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_name, non_unique " +
                     "FROM information_schema.statistics " +
                     "WHERE table_schema=? AND table_name='wishlist' AND index_name='uq_wishlist_user_furniture' " +
                     "ORDER BY seq_in_index")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("first index column").isTrue();
                assertThat(rs.getString("column_name")).isEqualTo("user_id");
                assertThat(rs.getInt("non_unique")).isZero();
                assertThat(rs.next()).as("second index column").isTrue();
                assertThat(rs.getString("column_name")).isEqualTo("furniture_id");
                assertThat(rs.next()).as("no third index column").isFalse();
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-3 — duplicate insert fails on the UNIQUE constraint.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-3: duplicate (user_id, furniture_id) insert → SQLIntegrityConstraintViolation")
    void ac3_uniqueConstraintRejectsDuplicate() throws SQLException {
        String fid = anyFurnitureId();
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(String.format(
                    "INSERT INTO wishlist (wishlist_id, user_id, furniture_id, category, price, status) " +
                    "VALUES ('w_dup_1', 'u1', '%s', 'desk', 10000, 'Active')", fid));
        }
        // Second insert must fail on the UNIQUE key.
        assertThatThrownBy(() -> {
            try (Connection c = conn(); Statement st = c.createStatement()) {
                st.executeUpdate(String.format(
                        "INSERT INTO wishlist (wishlist_id, user_id, furniture_id, category, price, status) " +
                        "VALUES ('w_dup_2', 'u1', '%s', 'desk', 10000, 'Active')", fid));
            }
        }).hasMessageContaining("uq_wishlist_user_furniture");

        // Clean up so subsequent tests are not polluted.
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM wishlist WHERE wishlist_id='w_dup_1'");
        }
    }

    // -----------------------------------------------------------------
    // AC-4 — V1..V5 schema history preserved (success=1).
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-4: flyway_schema_history V1..V6 all success=1")
    void ac4_historyRowsPresent() throws SQLException {
        for (String v : new String[] { "1", "2", "3", "4", "5", "6" }) {
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
}
