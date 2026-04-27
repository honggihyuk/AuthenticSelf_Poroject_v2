package com.authenticself.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration test for the V1 Flyway migration.
 * <p>
 * Spins up a real MySQL 8 container, runs Flyway against the repo's
 * {@code db/migration} directory, and asserts every acceptance criterion
 * AC-1 through AC-17 from {@code artifacts/DB-schema-init/acceptance_criteria.json}.
 * <p>
 * Requires Docker on the host. Run via {@code ./gradlew test}.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V1InitSchemaMigrationTest {

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
    // AC-1 — exactly four business tables exist (plus flyway_schema_history)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-1: schema contains exactly users, spaces, furniture, wishlist (+ flyway_schema_history)")
    void ac1_tablesPresent() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }
        }
        assertThat(tables).containsExactlyInAnyOrder(
                "users", "spaces", "furniture", "wishlist", "flyway_schema_history");
    }

    // -----------------------------------------------------------------
    // AC-2 — users columns
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-2: users has required columns with correct flags")
    void ac2_usersColumns() throws SQLException {
        assertColumn("users", "user_id", "varchar", false, "PRI");
        assertColumn("users", "name", "varchar", false, "");
        assertColumn("users", "email", "varchar", false, "UNI");
        assertColumnExists("users", "created_at");
        assertColumnExists("users", "updated_at");
    }

    // -----------------------------------------------------------------
    // AC-3 — spaces columns
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-3: spaces has required columns with correct types")
    void ac3_spacesColumns() throws SQLException {
        assertColumn("spaces", "room_id", "varchar", false, "PRI");
        assertColumn("spaces", "user_id", "varchar", false, null);
        assertColumn("spaces", "dimensions", "varchar", false, "");
        assertColumn("spaces", "main_color", "varchar", false, "");
        assertColumn("spaces", "style", "varchar", false, "");
        assertColumn("spaces", "analysis_date", "datetime", false, "");
    }

    // -----------------------------------------------------------------
    // AC-4 — furniture columns (all VARCHAR NOT NULL)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-4: furniture has furniture_id PK + type/style/size varchar NOT NULL")
    void ac4_furnitureColumns() throws SQLException {
        assertColumn("furniture", "furniture_id", "varchar", false, "PRI");
        assertColumn("furniture", "type", "varchar", false, null);
        assertColumn("furniture", "style", "varchar", false, null);
        assertColumn("furniture", "size", "varchar", false, "");
    }

    // -----------------------------------------------------------------
    // AC-5 — wishlist columns (price INT, status native ENUM default Active)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-5: wishlist has int price + ENUM('Active','Purchased') status default 'Active'")
    void ac5_wishlistColumns() throws SQLException {
        assertColumn("wishlist", "wishlist_id", "varchar", false, "PRI");
        assertColumn("wishlist", "user_id", "varchar", false, null);
        assertColumn("wishlist", "furniture_id", "varchar", false, null);
        assertColumn("wishlist", "category", "varchar", false, "");
        assertColumn("wishlist", "price", "int", false, "");

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_type, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name='wishlist' AND column_name='status'")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("status column exists").isTrue();
                String type = rs.getString(1).toLowerCase().replace(" ", "");
                assertThat(type).isEqualTo("enum('active','purchased')");
                assertThat(rs.getString(2)).isEqualTo("NO");
                assertThat(rs.getString(3)).isEqualTo("Active");
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-6 — exactly one PK per table on expected column
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-6: each business table has exactly one PRIMARY KEY on the expected column")
    void ac6_primaryKeys() throws SQLException {
        assertPrimaryKey("users", "user_id");
        assertPrimaryKey("spaces", "room_id");
        assertPrimaryKey("furniture", "furniture_id");
        assertPrimaryKey("wishlist", "wishlist_id");
    }

    // -----------------------------------------------------------------
    // AC-7 — FK spaces.user_id rejects nonexistent user
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-7: inserting spaces with nonexistent user_id fails with FK error (1452)")
    void ac7_spacesFkRejectsOrphan() throws SQLException {
        try (Connection c = conn()) {
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO spaces(room_id, user_id, dimensions, main_color, style, analysis_date) " +
                        "VALUES('r1','u_nonexistent','1x1','#fff','Modern', NOW())")) {
                    ps.executeUpdate();
                }
            })
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(1452));
        }
    }

    // -----------------------------------------------------------------
    // AC-8 — FK wishlist.user_id rejects nonexistent user
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-8: inserting wishlist with nonexistent user_id fails with FK error (1452)")
    void ac8_wishlistFkUser() throws SQLException {
        try (Connection c = conn()) {
            // seed a furniture row so the failure is *specifically* on the user FK
            exec(c, "INSERT INTO furniture(furniture_id, type, style, size) VALUES('f_ac8','chair','Modern','40x40x90')");
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price) " +
                        "VALUES('w_ac8','u_nonexistent','f_ac8','chair', 100)")) {
                    ps.executeUpdate();
                }
            })
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(1452));
        }
    }

    // -----------------------------------------------------------------
    // AC-9 — FK wishlist.furniture_id rejects nonexistent furniture
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: inserting wishlist with nonexistent furniture_id fails with FK error (1452)")
    void ac9_wishlistFkFurniture() throws SQLException {
        try (Connection c = conn()) {
            exec(c, "INSERT INTO users(user_id, name, email) VALUES('u_ac9','Ada','ada.ac9@test.com')");
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price) " +
                        "VALUES('w_ac9','u_ac9','f_nonexistent','chair', 100)")) {
                    ps.executeUpdate();
                }
            })
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(1452));
        }
    }

    // -----------------------------------------------------------------
    // AC-10 — invalid ENUM value rejected; valid ones accepted
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-10: status='Pending' rejected; 'Active' and 'Purchased' accepted")
    void ac10_statusEnumEnforced() throws SQLException {
        try (Connection c = conn()) {
            // Enforce strict enum validation in this session (MySQL 8 is strict by default, but be explicit).
            exec(c, "SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'");
            exec(c, "INSERT INTO users(user_id, name, email) VALUES('u_ac10','Grace','grace.ac10@test.com')");
            exec(c, "INSERT INTO furniture(furniture_id, type, style, size) VALUES('f_ac10','desk','Modern','120x60x75')");

            // Invalid value → must fail
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price, status) " +
                        "VALUES('w_ac10_bad','u_ac10','f_ac10','desk', 100, 'Pending')")) {
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);

            // Valid values → must succeed
            exec(c, "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price, status) " +
                    "VALUES('w_ac10_a','u_ac10','f_ac10','desk', 100, 'Active')");
            exec(c, "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price, status) " +
                    "VALUES('w_ac10_p','u_ac10','f_ac10','desk', 100, 'Purchased')");
        }
    }

    // -----------------------------------------------------------------
    // AC-11 — default status = 'Active'
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-11: wishlist insert without status defaults to 'Active'")
    void ac11_defaultStatus() throws SQLException {
        try (Connection c = conn()) {
            exec(c, "INSERT INTO users(user_id, name, email) VALUES('u_ac11','Linus','linus.ac11@test.com')");
            exec(c, "INSERT INTO furniture(furniture_id, type, style, size) VALUES('f_ac11','bed','Classic','200x160x40')");
            exec(c, "INSERT INTO wishlist(wishlist_id, user_id, furniture_id, category, price) " +
                    "VALUES('w_ac11','u_ac11','f_ac11','bed', 599000)");

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status FROM wishlist WHERE wishlist_id='w_ac11'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Active");
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-12 — idx_spaces_user_id exists, on user_id, non-unique
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-12: idx_spaces_user_id exists on spaces.user_id (Non_unique=1)")
    void ac12_idxSpacesUserId() throws SQLException {
        assertNonUniqueIndex("spaces", "idx_spaces_user_id", "user_id");
    }

    // -----------------------------------------------------------------
    // AC-13 — idx_wishlist_user_id exists, on user_id, non-unique
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-13: idx_wishlist_user_id exists on wishlist.user_id (Non_unique=1)")
    void ac13_idxWishlistUserId() throws SQLException {
        assertNonUniqueIndex("wishlist", "idx_wishlist_user_id", "user_id");
    }

    // -----------------------------------------------------------------
    // AC-14 — exactly one V1__*.sql file, Flyway history row matches
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-14: single V1__init_schema.sql on disk, one success=1 row in flyway_schema_history")
    void ac14_flywayVersioning() throws Exception {
        // On-disk check (tests run with working dir = src/backend)
        Path migrations = Paths.get("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(migrations)) {
            List<String> v1Files = files
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("V1__") && n.endsWith(".sql"))
                    .toList();
            assertThat(v1Files).containsExactly("V1__init_schema.sql");
        }

        // flyway_schema_history row check
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT version, description, success FROM flyway_schema_history WHERE version='1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("1");
            assertThat(rs.getString("description")).isEqualTo("init schema");
            assertThat(rs.getBoolean("success")).isTrue();
            assertThat(rs.next()).as("only one row for version 1").isFalse();
        }
    }

    // -----------------------------------------------------------------
    // AC-15 — no uppercase letter in any column name across the 4 tables
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-15: no column name in the 4 business tables contains uppercase letters")
    void ac15_snakeCase() throws SQLException {
        Pattern hasUpper = Pattern.compile("[A-Z]");
        List<String> offenders = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name, column_name FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name IN ('users','spaces','furniture','wishlist')")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString(2);
                    if (hasUpper.matcher(col).find()) {
                        offenders.add(rs.getString(1) + "." + col);
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    // -----------------------------------------------------------------
    // AC-16 — utf8mb4_unicode_ci collation on all 4 tables
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-16: each business table has TABLE_COLLATION = utf8mb4_unicode_ci")
    void ac16_collation() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name, table_collation FROM information_schema.tables " +
                     "WHERE table_schema=? AND table_name IN ('users','spaces','furniture','wishlist')")) {
            ps.setString(1, DB_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    assertThat(rs.getString(2))
                            .as("collation of %s", rs.getString(1))
                            .isEqualTo("utf8mb4_unicode_ci");
                    count++;
                }
                assertThat(count).isEqualTo(4);
            }
        }
    }

    // -----------------------------------------------------------------
    // AC-17 — four entity stub files present with @Entity + correct @Table
    // Filesystem + source-text check (no classpath reflection needed).
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("AC-17: domain entity stubs")
    class Ac17EntityStubs {
        private final Path domainDir = Paths.get("src/main/java/com/authenticself/domain");

        @Test
        void exactlyFourEntityFiles() throws Exception {
            try (Stream<Path> files = Files.list(domainDir)) {
                List<String> names = files
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".java"))
                        .sorted()
                        .toList();
                assertThat(names).containsExactly(
                        "Furniture.java", "Space.java", "User.java", "Wishlist.java");
            }
        }

        @Test
        void eachEntityHasEntityAndTableAnnotations() throws Exception {
            assertEntity("User.java",      "users");
            assertEntity("Space.java",     "spaces");
            assertEntity("Furniture.java", "furniture");
            assertEntity("Wishlist.java",  "wishlist");
        }

        private void assertEntity(String file, String tableName) throws Exception {
            String src = Files.readString(domainDir.resolve(file));
            assertThat(src).as("%s must carry @Entity", file).contains("@Entity");
            assertThat(src).as("%s must carry @Table(name=\"%s\")", file, tableName)
                    .containsPattern("@Table\\s*\\(\\s*name\\s*=\\s*\"" + tableName + "\"");
        }
    }

    // =================================================================
    // helpers
    // =================================================================

    private void assertColumn(String table, String col, String expectedTypePrefix,
                              boolean nullable, String expectedKey) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, is_nullable, column_key FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name=? AND column_name=?")) {
            ps.setString(1, DB_NAME);
            ps.setString(2, table);
            ps.setString(3, col);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("column %s.%s exists", table, col).isTrue();
                assertThat(rs.getString(1).toLowerCase()).startsWith(expectedTypePrefix);
                assertThat(rs.getString(2)).isEqualTo(nullable ? "YES" : "NO");
                if (expectedKey != null) {
                    assertThat(rs.getString(3)).isEqualTo(expectedKey);
                }
            }
        }
    }

    private void assertColumnExists(String table, String col) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM information_schema.columns " +
                     "WHERE table_schema=? AND table_name=? AND column_name=?")) {
            ps.setString(1, DB_NAME);
            ps.setString(2, table);
            ps.setString(3, col);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("column %s.%s exists", table, col).isTrue();
            }
        }
    }

    private void assertPrimaryKey(String table, String expectedCol) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT k.column_name FROM information_schema.table_constraints t " +
                     "JOIN information_schema.key_column_usage k " +
                     "  ON t.constraint_name = k.constraint_name " +
                     " AND t.table_schema   = k.table_schema " +
                     " AND t.table_name     = k.table_name " +
                     "WHERE t.table_schema=? AND t.table_name=? AND t.constraint_type='PRIMARY KEY'")) {
            ps.setString(1, DB_NAME);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertThat(cols).as("PK of %s", table).containsExactly(expectedCol);
            }
        }
    }

    private void assertNonUniqueIndex(String table, String idxName, String column) throws SQLException {
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SHOW INDEX FROM " + table + " WHERE Key_name='" + idxName + "'")) {
            boolean found = false;
            while (rs.next()) {
                if (found) fail("index " + idxName + " returned more than one row");
                assertThat(rs.getString("Column_name")).isEqualTo(column);
                assertThat(rs.getInt("Non_unique")).isEqualTo(1);
                found = true;
            }
            assertThat(found).as("index %s on %s exists", idxName, table).isTrue();
        }
    }

    private void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
