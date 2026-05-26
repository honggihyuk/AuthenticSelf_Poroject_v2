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
 * Testcontainers integration test for the V11 Flyway migration
 * (UC-SECURE-AUTH AC-7 / AC-8 / AC-19).
 *
 * <p>Spins up MySQL 8, runs Flyway through V1..V11, and asserts:
 * <ol>
 *   <li>{@code users.password_hash} column shape:
 *       {@code VARCHAR(72) NOT NULL} (AC-8).</li>
 *   <li>flyway_schema_history shows V1..V11 all success=1 (AC-19).</li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V11PasswordHashMigrationTest {

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
    // AC-8 — column shape.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-8: users.password_hash is VARCHAR(72) NOT NULL")
    void columnShape_ac8() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, character_maximum_length, is_nullable " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='users' AND column_name='password_hash'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("password_hash column exists").isTrue();
                assertThat(rs.getString("data_type")).isEqualToIgnoringCase("varchar");
                assertThat(rs.getLong("character_maximum_length")).isEqualTo(72L);
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("NO");
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-19 — V1..V11 all success in flyway_schema_history.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-19: flyway_schema_history V1..V11 all success=1")
    void historySuccessThroughV11_ac19() throws SQLException {
        for (String v : new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11" }) {
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
