package com.authenticself.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the V2 Flyway migration
 * ({@code V2__add_spaces_status_and_photo.sql}).
 * <p>
 * Covers UC-01-photo-upload AC-1 through AC-5 — the SQL-shape acceptance
 * criteria that require a real MySQL instance. The endpoint-layer ACs
 * (AC-6..AC-13) live in the MockMvc/integration tests under
 * {@code com.authenticself.controller}.
 * <p>
 * Requires Docker on the host. Run via {@code ./gradlew test}.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2SpacesMigrationTest {

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
        this.user = MYSQL.getUsername();
        this.pass = MYSQL.getPassword();

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
    // AC-1 — V2 file present + is the only V2 file.
    // Static filesystem check, no DB needed but kept here for locality.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-1: V2__add_spaces_status_and_photo.sql is the only V2__*.sql file")
    void ac1_v2FileUnique() throws Exception {
        Path migrationDir = Paths.get("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(migrationDir)) {
            List<Path> v2Files = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("V2__") && name.endsWith(".sql");
                    })
                    .toList();
            assertThat(v2Files).hasSize(1);
            assertThat(v2Files.get(0).getFileName().toString())
                    .isEqualTo("V2__add_spaces_status_and_photo.sql");
        }
    }

    // -----------------------------------------------------------------
    // AC-2 — flyway_schema_history has V1 + V2, both success=1.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-2: flyway_schema_history has V1 and V2 both success=1")
    void ac2_flywayHistoryHasV1AndV2() throws SQLException {
        Map<String, Boolean> versionToSuccess = new HashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT version, success FROM flyway_schema_history " +
                     "WHERE version IN ('1','2')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versionToSuccess.put(rs.getString(1), rs.getBoolean(2));
                }
            }
        }
        assertThat(versionToSuccess).containsKeys("1", "2");
        assertThat(versionToSuccess.get("1")).isTrue();
        assertThat(versionToSuccess.get("2")).isTrue();
    }

    // -----------------------------------------------------------------
    // AC-3 — new V2 columns exist with correct types, nullability, defaults.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-3: V2 adds photo_url / original_filename / content_type / file_size_bytes / status / uploaded_at")
    void ac3_v2ColumnsPresent() throws SQLException {
        // photo_url: VARCHAR(512) NOT NULL
        assertColumnMatches("spaces", "photo_url", "varchar", 512, false, null);
        // original_filename: VARCHAR(255) NULL
        assertColumnMatches("spaces", "original_filename", "varchar", 255, true, null);
        // content_type: VARCHAR(64) NOT NULL
        assertColumnMatches("spaces", "content_type", "varchar", 64, false, null);
        // file_size_bytes: BIGINT NOT NULL
        assertColumnMatches("spaces", "file_size_bytes", "bigint", null, false, null);

        // status: ENUM('PENDING_ANALYSIS','ANALYZED','FAILED') NOT NULL DEFAULT 'PENDING_ANALYSIS'
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_type, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='spaces' AND column_name='status'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("spaces.status column exists").isTrue();
                String columnType = rs.getString("column_type").toLowerCase();
                assertThat(columnType)
                        .contains("enum")
                        .contains("pending_analysis")
                        .contains("analyzed")
                        .contains("failed");
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("NO");
                assertThat(rs.getString("column_default")).isEqualTo("PENDING_ANALYSIS");
            }
        }

        // uploaded_at: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, is_nullable, column_default, extra " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='spaces' AND column_name='uploaded_at'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("spaces.uploaded_at column exists").isTrue();
                assertThat(rs.getString("data_type")).isEqualToIgnoringCase("datetime");
                assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("NO");
                // MySQL renders this default as either 'CURRENT_TIMESTAMP' (as
                // a string) or via the `extra` column — accept either.
                String def = rs.getString("column_default");
                String extra = rs.getString("extra");
                boolean hasDefault =
                        (def != null && def.toUpperCase().contains("CURRENT_TIMESTAMP")) ||
                        (extra != null && extra.toUpperCase().contains("CURRENT_TIMESTAMP"));
                assertThat(hasDefault)
                        .as("uploaded_at default is CURRENT_TIMESTAMP (col_default=%s, extra=%s)", def, extra)
                        .isTrue();
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-4 — V2 relaxes 4 pre-existing spaces columns to NULL.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-4: dimensions/main_color/style/analysis_date are nullable after V2")
    void ac4_analysisColumnsNullable() throws SQLException {
        for (String col : List.of("dimensions", "main_color", "style", "analysis_date")) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT is_nullable FROM information_schema.columns " +
                         "WHERE table_schema=? AND table_name='spaces' AND column_name=?")) {
                ps.setString(1, DB_NAME);
                ps.setString(2, col);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("spaces.%s column exists", col).isTrue();
                    assertThat(rs.getString("is_nullable"))
                            .as("spaces.%s is nullable after V2", col)
                            .isEqualToIgnoringCase("YES");
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-5 — idx_spaces_status is present with Column_name='status'.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-5: idx_spaces_status index exists on spaces.status")
    void ac5_indexOnStatus() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SHOW INDEX FROM spaces WHERE Key_name='idx_spaces_status'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("idx_spaces_status row exists").isTrue();
                assertThat(rs.getString("Column_name")).isEqualTo("status");
                // Must be a non-unique secondary index.
                assertThat(rs.getInt("Non_unique")).isEqualTo(1);
                assertThat(rs.next()).as("idx_spaces_status is single-column").isFalse();
            }
        }
    }

    // -----------------------------------------------------------------
    // Helper: assert a column exists with the given data type, size,
    // nullability, and default.
    // -----------------------------------------------------------------
    private void assertColumnMatches(
            String table,
            String column,
            String expectedDataType,
            Integer expectedMaxLength,
            boolean expectNullable,
            String expectedDefault
    ) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, character_maximum_length, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name=? AND column_name=?")) {
            ps.setString(1, DB_NAME);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("%s.%s column exists", table, column)
                        .isTrue();
                assertThat(rs.getString("data_type"))
                        .as("%s.%s data_type", table, column)
                        .isEqualToIgnoringCase(expectedDataType);
                if (expectedMaxLength != null) {
                    assertThat(rs.getInt("character_maximum_length"))
                            .as("%s.%s character_maximum_length", table, column)
                            .isEqualTo(expectedMaxLength);
                }
                assertThat(rs.getString("is_nullable"))
                        .as("%s.%s is_nullable", table, column)
                        .isEqualToIgnoringCase(expectNullable ? "YES" : "NO");
                if (expectedDefault != null) {
                    assertThat(rs.getString("column_default"))
                            .as("%s.%s column_default", table, column)
                            .isEqualTo(expectedDefault);
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Migration order sanity — V1 applied before V2 in the history.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("migration order: V1 applied before V2")
    void migrationOrder() {
        Flyway fw = Flyway.configure()
                .dataSource(jdbcUrl, user, pass)
                .locations("filesystem:src/main/resources/db/migration")
                .load();
        MigrationInfo[] info = fw.info().applied();
        assertThat(info.length).isGreaterThanOrEqualTo(2);
        assertThat(info[0].getVersion().toString()).isEqualTo("1");
        assertThat(info[1].getVersion().toString()).isEqualTo("2");
    }
}
