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
 * Integration test for the V7 Flyway migration (UC-03-admin-overview
 * AC-2 / AC-3 / AC-4 / AC-5).
 * <p>
 * Spins up MySQL 8 via Testcontainers, runs Flyway through V1..V7, and
 * asserts:
 * <ol>
 *   <li>{@code users.role} column has DATA_TYPE='enum', COLUMN_TYPE
 *       matching ENUM('USER','ADMIN'), IS_NULLABLE='NO',
 *       COLUMN_DEFAULT='USER' (AC-2).</li>
 *   <li>INFORMATION_SCHEMA.STATISTICS shows an index
 *       {@code idx_users_role} on column {@code role} (AC-2).</li>
 *   <li>Inserting {@code role='SUPERVISOR'} is rejected;
 *       {@code role='USER'} + {@code role='ADMIN'} both succeed (AC-3).</li>
 *   <li>Inserting without a {@code role} value defaults to
 *       {@code 'USER'} (AC-4).</li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V7UsersRoleMigrationTest {

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
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, pass);
    }

    // -----------------------------------------------------------------
    // AC-2 — users.role column + idx_users_role index.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-2: users.role is ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'")
    void columnAndIndexShape_ac2() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, column_type, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='users' AND column_name='role'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("role column exists").isTrue();
                assertThat(rs.getString("data_type")).isEqualToIgnoringCase("enum");
                String colType = rs.getString("column_type");
                assertThat(colType.toUpperCase())
                        .contains("ENUM").contains("USER").contains("ADMIN");
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("NO");
                assertThat(rs.getString("column_default")).isEqualTo("USER");
            }
        }

        // Index presence — statistics row per column in the index.
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_name FROM information_schema.statistics " +
                     "WHERE table_schema=? AND table_name='users' AND index_name='idx_users_role' " +
                     "ORDER BY seq_in_index")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("idx_users_role row").isTrue();
                assertThat(rs.getString("column_name")).isEqualToIgnoringCase("role");
                assertThat(rs.next()).as("no second column").isFalse();
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-3 — ENUM rejects unknown values; accepts USER + ADMIN.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-3: role='SUPERVISOR' is rejected; USER and ADMIN succeed")
    void enumRejectsUnknownValue_ac3() throws SQLException {
        // STRICT sql_mode on Testcontainers MySQL 8 rejects out-of-enum values.
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate("SET SESSION sql_mode='STRICT_ALL_TABLES'");
        }

        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email, role) " +
                    "VALUES ('u_ac3_user', 'UserA', 'usera@example.com', 'USER')");
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email, role) " +
                    "VALUES ('u_ac3_admin', 'Adm', 'adm@example.com', 'ADMIN')");
        }

        assertThatThrownBy(() -> {
            try (Connection c = conn(); Statement st = c.createStatement()) {
                st.executeUpdate(
                        "INSERT INTO users (user_id, name, email, role) " +
                        "VALUES ('u_ac3_bad', 'Bad', 'bad@example.com', 'SUPERVISOR')");
            }
        }).hasMessageContaining("truncated");
    }

    // -----------------------------------------------------------------
    // AC-4 — DEFAULT 'USER' applied when no role supplied.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-4: inserting without role defaults to USER")
    void defaultIsUser_ac4() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email) " +
                    "VALUES ('u_ac4_def', 'Def', 'def@example.com')");
        }
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT role FROM users WHERE user_id='u_ac4_def'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("role")).isEqualTo("USER");
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-5 — V1..V7 all success in flyway_schema_history.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-5: flyway_schema_history V1..V7 all success=1")
    void historySuccessThroughV7_ac5() throws SQLException {
        for (String v : new String[] { "1", "2", "3", "4", "5", "6", "7" }) {
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
