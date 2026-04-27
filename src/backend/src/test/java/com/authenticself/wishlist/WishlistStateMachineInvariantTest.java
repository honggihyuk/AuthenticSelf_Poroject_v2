package com.authenticself.wishlist;

import com.authenticself.domain.Wishlist;
import com.authenticself.repository.FurnitureRepository;
import com.authenticself.repository.WishlistRepository;
import com.authenticself.wishlist.dto.AddWishlistRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-style invariant test for the wishlist state machine
 * (UC-02-wishlist AC-52).
 * <p>
 * After every service operation ({@code add}, {@code patch}, {@code delete})
 * the following invariants must hold for every surviving row:
 * <ul>
 *   <li>{@code status ∈ {Active, Purchased}}</li>
 *   <li>{@code status == Active    ⇔ purchased_at IS NULL}</li>
 *   <li>{@code status == Purchased ⇔ purchased_at IS NOT NULL}</li>
 * </ul>
 * We exercise the full valid transition graph (add, no-op PATCH,
 * Active→Purchased, Purchased→Active, DELETE) plus two negative paths
 * (invalid enum PATCH and foreign-owner PATCH) and re-check the
 * invariants after each.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ContextConfiguration(initializers = WishlistStateMachineInvariantTest.DbInit.class)
class WishlistStateMachineInvariantTest {

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
                    "app.ai.poller.enabled=false"
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired WishlistService     service;
    @Autowired WishlistRepository  wishlists;
    @Autowired FurnitureRepository furniture;

    @BeforeAll
    void seedUsers() throws SQLException {
        try (Connection c = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO users (user_id, name, email) VALUES " +
                    "('u_inv_1', 'Inv1', 'i1@example.com')," +
                    "('u_inv_2', 'Inv2', 'i2@example.com') " +
                    "ON DUPLICATE KEY UPDATE name=name");
        }
    }

    @Test
    @DisplayName("AC-52: state-machine invariant holds after every operation")
    void invariantHoldsAfterEveryOp_ac52() {
        String fid = furniture.findAll().stream().findFirst().orElseThrow().getFurnitureId();

        // 1. add → Active, purchased_at == null
        var out = service.add("u_inv_1", new AddWishlistRequest(fid, "desk", 100));
        String wid = out.body().wishlistId();
        assertInvariants();

        // 2. no-op PATCH (Active → Active) → still Active, unchanged
        service.patch("u_inv_1", wid, "ACTIVE");
        assertInvariants();

        // 3. Active → Purchased → purchased_at NOT NULL
        service.patch("u_inv_1", wid, "PURCHASED");
        Wishlist afterPurchase = wishlists.findById(wid).orElseThrow();
        assertThat(afterPurchase.getStatus()).isEqualTo(Wishlist.Status.Purchased);
        assertThat(afterPurchase.getPurchasedAt()).isNotNull();
        assertInvariants();

        // 4. no-op PATCH (Purchased → Purchased) → still Purchased
        service.patch("u_inv_1", wid, "PURCHASED");
        assertInvariants();

        // 5. Purchased → Active → purchased_at cleared
        service.patch("u_inv_1", wid, "ACTIVE");
        Wishlist afterUnMark = wishlists.findById(wid).orElseThrow();
        assertThat(afterUnMark.getStatus()).isEqualTo(Wishlist.Status.Active);
        assertThat(afterUnMark.getPurchasedAt()).isNull();
        assertInvariants();

        // 6. Invalid PATCH — row must not change, invariants still hold.
        assertThatThrownBy(() -> service.patch("u_inv_1", wid, "PENDING"))
                .isInstanceOf(WishlistException.class);
        assertInvariants();

        // 7. Foreign-user PATCH — must 404, row must not change.
        assertThatThrownBy(() -> service.patch("u_inv_2", wid, "PURCHASED"))
                .isInstanceOf(WishlistException.class);
        assertInvariants();

        // 8. DELETE while Active — row removed, invariants across the
        //    remaining rows still hold.
        service.delete("u_inv_1", wid);
        assertThat(wishlists.findById(wid)).isEmpty();
        assertInvariants();
    }

    /** Scan every wishlist row for the invariant. */
    private void assertInvariants() {
        for (Wishlist w : wishlists.findAll()) {
            assertThat(w.getStatus())
                    .as("wishlist_id=%s status is Active or Purchased", w.getWishlistId())
                    .isIn(Wishlist.Status.Active, Wishlist.Status.Purchased);

            if (w.getStatus() == Wishlist.Status.Active) {
                assertThat(w.getPurchasedAt())
                        .as("Active row wishlist_id=%s must have purchased_at=null", w.getWishlistId())
                        .isNull();
            } else {
                assertThat(w.getPurchasedAt())
                        .as("Purchased row wishlist_id=%s must have purchased_at!=null", w.getWishlistId())
                        .isNotNull();
            }
        }
    }
}
