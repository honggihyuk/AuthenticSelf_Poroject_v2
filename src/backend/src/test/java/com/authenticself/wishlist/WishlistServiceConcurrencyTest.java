package com.authenticself.wishlist;

import com.authenticself.domain.Wishlist;
import com.authenticself.repository.FurnitureRepository;
import com.authenticself.repository.UserRepository;
import com.authenticself.repository.WishlistRepository;
import com.authenticself.wishlist.dto.AddWishlistRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for the wishlist add path (UC-02-wishlist AC-15).
 * <p>
 * Spins up a real MySQL 8 + Spring context so the
 * {@code uq_wishlist_user_furniture} UNIQUE constraint is the concurrency
 * primitive under test. Two parallel {@code service.add(...)} calls on
 * the same {@code (user_id, furniture_id)} tuple must:
 * <ol>
 *   <li>produce exactly ONE row in {@code wishlist};</li>
 *   <li>both return {@code alreadyExists=true} or one-created-one-hit
 *       (never two different {@code wishlistId}s).</li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ContextConfiguration(initializers = WishlistServiceConcurrencyTest.DbInit.class)
class WishlistServiceConcurrencyTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("authenticself")
            .withUsername("test")
            .withPassword("test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("characterEncoding", "utf8mb4");

    static class DbInit implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            MYSQL.start();
            TestPropertyValues.of(
                    "spring.datasource.url="      + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.jpa.hibernate.ddl-auto=validate",
                    // default max-items cap.
                    "app.wishlist.max-items-per-user=500",
                    // Silence the AI poller + background tasks — this
                    // test only exercises the wishlist path.
                    "app.ai.poller.enabled=false"
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired WishlistService     service;
    @Autowired WishlistRepository  wishlists;
    @Autowired UserRepository      users;
    @Autowired FurnitureRepository furniture;

    @BeforeAll
    void seedUser() throws SQLException {
        // Flyway runs via Spring Boot — the user row isn't part of the
        // seed migration. Use a raw JDBC call so we don't depend on the
        // User entity's generated columns.
        try (Connection c = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email) VALUES " +
                    "('u_conc', 'Conc', 'c@example.com') " +
                    "ON DUPLICATE KEY UPDATE name=name");
        }
    }

    // -----------------------------------------------------------------
    // AC-15 — two parallel adds produce exactly one row.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-15: parallel add() for (u,f) produces exactly one row")
    void parallelAddCreatesOneRow_ac15() throws Exception {
        // pick any seeded furniture row.
        String fid = furniture.findAll().stream().findFirst().orElseThrow().getFurnitureId();
        String uid = "u_conc";

        // Ensure no leftover row from a previous run.
        wishlists.findByUserIdAndFurnitureId(uid, fid).ifPresent(w ->
                wishlists.deleteById(w.getWishlistId()));

        int parallelism = 8;
        ExecutorService exec = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<WishlistService.Outcome<?>>> futures = new ArrayList<>();
        for (int i = 0; i < parallelism; i++) {
            final int n = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                // synchronise-ish — small sleep for more contention.
                try { Thread.sleep(5L * (n % 3)); } catch (InterruptedException ignored) {}
                return service.add(uid, new AddWishlistRequest(fid, "desk", 189000));
            }, exec));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        exec.shutdown();

        long rows = wishlists.findAllByUserIdOrderByUpdatedAtDesc(uid).stream()
                .filter(w -> w.getFurnitureId().equals(fid))
                .count();
        assertThat(rows).isEqualTo(1);

        // Exactly one of the N calls should report created=true.
        long createdCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
                })
                .filter(WishlistService.Outcome::created)
                .count();
        assertThat(createdCount).isEqualTo(1L);

        // Clean up so the test is re-runnable.
        wishlists.findByUserIdAndFurnitureId(uid, fid).ifPresent(w ->
                wishlists.deleteById(w.getWishlistId()));
    }

    // -----------------------------------------------------------------
    // Helper: run Flyway against the container in case Spring's profile
    // skipped it (defensive — should be a no-op normally).
    // -----------------------------------------------------------------
    @SuppressWarnings("unused")
    private static void runMigrations() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }
}
